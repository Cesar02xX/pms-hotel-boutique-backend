#!/usr/bin/env bash
# Configura SonarQube self-hosted para el backend:
#   1. Espera a que el servidor esté UP.
#   2. Cambia la contraseña inicial de admin (si sigue siendo admin/admin).
#   3. Crea el proyecto backend.
#   4. Crea el quality gate "Aurora Backend" con los umbrales del equipo y lo asigna al proyecto.
#   5. Genera un token de análisis de proyecto (se imprime una sola vez).
#
# Uso:
#   SONAR_ADMIN_PASSWORD='NuevaClave#2026' ./scripts/sonarqube/setup-sonarqube.sh
#
# Variables (opcionales salvo SONAR_ADMIN_PASSWORD):
#   SONAR_HOST_URL        default http://localhost:9000
#   SONAR_PROJECT_KEY     default pms-hotel-boutique-backend
#   SONAR_PROJECT_NAME    default "PMS Hotel Boutique Backend"
#   SONAR_ADMIN_PASSWORD  nueva contraseña de admin (mín. 12 caracteres, mayúscula, minúscula, número y símbolo)
#   SONAR_GATE_NAME       default "Aurora Backend"
#   SONAR_TOKEN_NAME      default ci-<projectKey>
set -euo pipefail

SONAR_HOST_URL="${SONAR_HOST_URL:-http://localhost:9000}"
SONAR_HOST_URL="${SONAR_HOST_URL%/}"
SONAR_PROJECT_KEY="${SONAR_PROJECT_KEY:-pms-hotel-boutique-backend}"
SONAR_PROJECT_NAME="${SONAR_PROJECT_NAME:-PMS Hotel Boutique Backend}"
SONAR_GATE_NAME="${SONAR_GATE_NAME:-Aurora Backend}"
SONAR_TOKEN_NAME="${SONAR_TOKEN_NAME:-ci-${SONAR_PROJECT_KEY}}"

if [[ -z "${SONAR_ADMIN_PASSWORD:-}" ]]; then
  echo "ERROR: define SONAR_ADMIN_PASSWORD con la contraseña (nueva o actual) del usuario admin." >&2
  exit 1
fi

api() {
  # api <METHOD> <path> [curl args...] -> imprime body, falla si HTTP >= 400
  local method="$1" path="$2"
  shift 2
  local response status
  response=$(curl -sS -u "admin:${SONAR_ADMIN_PASSWORD}" -X "$method" -w '\n%{http_code}' "${SONAR_HOST_URL}${path}" "$@")
  status="${response##*$'\n'}"
  response="${response%$'\n'*}"
  if [[ "$status" -ge 400 ]]; then
    echo "$response" >&2
    return 1
  fi
  echo "$response"
}

echo ">> Esperando a SonarQube en ${SONAR_HOST_URL} ..."
for _ in $(seq 1 60); do
  if curl -fsS "${SONAR_HOST_URL}/api/system/status" 2>/dev/null | grep -q '"status":"UP"'; then
    break
  fi
  sleep 5
done
curl -fsS "${SONAR_HOST_URL}/api/system/status" | grep -q '"status":"UP"' || {
  echo "ERROR: SonarQube no respondió UP a tiempo." >&2
  exit 1
}

echo ">> Contraseña inicial de admin"
if curl -fsS -u admin:admin "${SONAR_HOST_URL}/api/authentication/validate" | grep -q '"valid":true'; then
  curl -fsS -u admin:admin -X POST "${SONAR_HOST_URL}/api/users/change_password" \
    --data-urlencode "login=admin" \
    --data-urlencode "previousPassword=admin" \
    --data-urlencode "password=${SONAR_ADMIN_PASSWORD}"
  echo "   contraseña de admin actualizada."
else
  echo "   admin/admin ya no es válido; se usa SONAR_ADMIN_PASSWORD."
fi

echo ">> Proyecto ${SONAR_PROJECT_KEY}"
if api GET "/api/projects/search?projects=${SONAR_PROJECT_KEY}" | grep -q "\"key\":\"${SONAR_PROJECT_KEY}\""; then
  echo "   ya existe."
else
  api POST /api/projects/create \
    --data-urlencode "project=${SONAR_PROJECT_KEY}" \
    --data-urlencode "name=${SONAR_PROJECT_NAME}" \
    --data-urlencode "mainBranch=main" >/dev/null
  echo "   creado."
fi

echo ">> Quality gate \"${SONAR_GATE_NAME}\""
if api GET "/api/qualitygates/show" --get --data-urlencode "name=${SONAR_GATE_NAME}" >/dev/null 2>&1; then
  echo "   ya existe; se recrean sus condiciones."
  api POST /api/qualitygates/destroy --data-urlencode "name=${SONAR_GATE_NAME}" >/dev/null
fi
api POST /api/qualitygates/create --data-urlencode "name=${SONAR_GATE_NAME}" >/dev/null

# Las versiones recientes crean el gate con condiciones "Clean as You Code" por defecto
# (coverage 80%, 0 issues nuevos, hotspots 100%). Se eliminan para dejar solo los umbrales del equipo.
for condition_id in $(api GET "/api/qualitygates/show" --get --data-urlencode "name=${SONAR_GATE_NAME}" \
  | grep -o '"id":"[^"]*","metric"' | sed 's/"id":"\([^"]*\)".*/\1/'); do
  api POST /api/qualitygates/delete_condition --data-urlencode "id=${condition_id}" >/dev/null
done

add_condition() {
  # add_condition <descripción> <op> <umbral> <métrica> [métrica alternativa (modo MQR)]
  local label="$1" op="$2" error="$3"
  shift 3
  local metric
  for metric in "$@"; do
    if api POST /api/qualitygates/create_condition \
      --data-urlencode "gateName=${SONAR_GATE_NAME}" \
      --data-urlencode "metric=${metric}" \
      --data-urlencode "op=${op}" \
      --data-urlencode "error=${error}" >/dev/null 2>&1; then
      echo "   + ${label} (${metric} ${op} ${error})"
      return 0
    fi
  done
  echo "   ! no se pudo crear la condición: ${label}" >&2
  return 1
}

# Rating: 1=A, 2=B ... ; "GT 1" => falla si es peor que A.
add_condition "0 bugs nuevos"               GT 0  new_bugs                     new_software_quality_reliability_issues
add_condition "0 vulnerabilidades nuevas"   GT 0  new_vulnerabilities          new_software_quality_security_issues
add_condition "Rating A en code smells"     GT 1  new_maintainability_rating   new_software_quality_maintainability_rating
add_condition "Coverage >= 70% código nuevo" LT 70 new_coverage
add_condition "Duplicación <= 3%"           GT 3  new_duplicated_lines_density

api POST /api/qualitygates/select \
  --data-urlencode "gateName=${SONAR_GATE_NAME}" \
  --data-urlencode "projectKey=${SONAR_PROJECT_KEY}" >/dev/null
echo "   asignado a ${SONAR_PROJECT_KEY}."

echo ">> Token de análisis ${SONAR_TOKEN_NAME}"
api POST /api/user_tokens/revoke --data-urlencode "name=${SONAR_TOKEN_NAME}" >/dev/null 2>&1 || true
token_json=$(api POST /api/user_tokens/generate \
  --data-urlencode "name=${SONAR_TOKEN_NAME}" \
  --data-urlencode "type=PROJECT_ANALYSIS_TOKEN" \
  --data-urlencode "projectKey=${SONAR_PROJECT_KEY}")
token=$(echo "$token_json" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

cat <<EOF

SonarQube listo.
  SONAR_HOST_URL=${SONAR_HOST_URL}
  SONAR_PROJECT_KEY=${SONAR_PROJECT_KEY}
  SONAR_TOKEN=${token}

Guarda el token ahora (no se vuelve a mostrar) en .env y en los secrets de GitHub/Jenkins.
EOF
