# CI/CD Backend — PMS Hotel Boutique Aurora

Este documento describe los pipelines del backend (GitHub Actions y Jenkins), SonarQube self-hosted, el quality gate, la publicación de releases e imágenes Docker y el deploy.

## Resumen

| Evento | Pipeline | Qué hace |
| --- | --- | --- |
| PR hacia `develop`, `development` o `main` | `.github/workflows/backend-ci.yml` | `mvn clean verify` (tests + JaCoCo), reportes como artefactos, análisis SonarQube + quality gate (si hay secrets), `docker build` sin push |
| Push a `main` | `.github/workflows/backend-release.yml` | Reejecuta la verificación, crea tag `vX.Y.Z` + GitHub Release con el `.jar`, publica la imagen en GHCR y ejecuta el deploy (placeholder) |
| Push de tag `v*` | `.github/workflows/backend-release.yml` | Igual que `main`, usando la versión del tag |
| Jenkins on-premise | `Jenkinsfile` | Mismas etapas: build, tests, SonarQube, `.jar` versionado, Docker/GHCR y deploy en `main` |

Ninguna credencial está en el repositorio: todo llega por secrets de GitHub o credenciales de Jenkins. Si faltan, la etapa correspondiente **se omite sin romper el pipeline**.

## Variables y secrets

### GitHub → Settings → Secrets and variables → Actions

| Secret | Obligatorio | Uso |
| --- | --- | --- |
| `SONAR_TOKEN` | No (sin él se omite SonarQube) | Token de análisis del proyecto (tipo *Project Analysis Token*) |
| `SONAR_HOST_URL` | No (sin él se omite SonarQube) | URL pública del servidor SonarQube, p. ej. `https://sonar.midominio.com` |
| `SONAR_PROJECT_KEY` | No (default `pms-hotel-boutique-backend` del `pom.xml`) | projectKey en SonarQube |
| `DEPLOY_HOST` | No (sin él se omite el deploy) | Host o IP del servidor productivo |
| `DEPLOY_KEY` | No (sin él se omite el deploy) | Llave privada SSH (contenido completo) |
| `DEPLOY_USER` | No (default `deploy`) | Usuario SSH |
| `DEPLOY_PATH` | No (default `/opt/pms-backend`) | Carpeta del servidor con el `docker-compose.yml` productivo |

> No se usa `SONAR_ORGANIZATION`: es exclusivo de SonarCloud. Este proyecto usa SonarQube self-hosted.

GHCR y los releases usan el `GITHUB_TOKEN` automático; no requieren secrets. Revisar en **Settings → Actions → General → Workflow permissions** que la organización/repo no bloquee los permisos `contents: write` y `packages: write` que declaran los workflows.

### Jenkins → Manage Jenkins → Credentials

| ID | Tipo | Equivale a |
| --- | --- | --- |
| `sonar-token` | Secret text | `SONAR_TOKEN` |
| `sonar-host-url` | Secret text | `SONAR_HOST_URL` |
| `sonar-project-key` | Secret text (opcional) | `SONAR_PROJECT_KEY` |
| `ghcr-credentials` | Username with password | Usuario GitHub + PAT con `write:packages` |
| `deploy-ssh-key` | SSH Username with private key | `DEPLOY_USER` + `DEPLOY_KEY` |
| `deploy-host` | Secret text | `DEPLOY_HOST` |
| `deploy-path` | Secret text (opcional) | `DEPLOY_PATH` |

El agente Jenkins necesita JDK 21, Docker, `git` y `curl`. Se recomienda un job *Multibranch Pipeline* apuntando a este repo, para que `branch 'main'` y `buildingTag()` funcionen.

### Local (`.env`)

```bash
cp .env.example .env
```

`docker compose` lee `.env` automáticamente (Postgres de la app y SonarQube). `.env` está en `.gitignore` y **nunca** se commitea.

## SonarQube self-hosted

### 1. Levantar el servidor

```bash
docker compose -f docker-compose.sonarqube.yml up -d
```

Levanta `sonarqube:community` (puerto **9000**) con su propio PostgreSQL (`sonarqube-db`, sin puerto expuesto) y volúmenes persistentes. El primer arranque tarda 1–3 minutos; está listo cuando:

```bash
curl http://localhost:9000/api/system/status   # {"status":"UP", ...}
```

> En Linux, si SonarQube se cae al iniciar por Elasticsearch: `sudo sysctl -w vm.max_map_count=524288`. Docker Desktop (Windows/macOS) normalmente no lo necesita.

### 2. Entrar a la UI

- URL: <http://localhost:9000>
- Usuario inicial: `admin`
- Contraseña inicial: `admin`

### 3. Configuración automática (recomendado)

El script deja todo configurado: cambia la contraseña inicial, crea el proyecto, crea y asigna el quality gate y genera el token.

```bash
SONAR_ADMIN_PASSWORD='UnaClaveSegura#2026' ./scripts/sonarqube/setup-sonarqube.sh
```

- La contraseña debe tener al menos 12 caracteres, con mayúscula, minúscula, número y símbolo.
- Si `admin/admin` ya no es válido, el script usa `SONAR_ADMIN_PASSWORD` como contraseña actual.
- Es idempotente: se puede volver a ejecutar. Recrea las condiciones del gate y regenera el token.
- Al final imprime `SONAR_TOKEN`, que se muestra una sola vez. Guárdalo en `.env` y en los secrets.

En Windows se ejecuta desde Git Bash.

### 4. Configuración manual (equivalente)

1. **Cambiar la contraseña inicial:** al entrar con `admin/admin`, SonarQube obliga a cambiarla. También se puede hacer desde *My Account → Security → Change password*.
2. **Crear el proyecto:** *Projects → Create project → Local project*:
   - Display name: `PMS Hotel Boutique Backend`
   - Project key: `pms-hotel-boutique-backend`
   - Main branch: `main`
3. **Quality gate:** *Quality Gates → Create* con el nombre `Aurora Backend`. Borrar las condiciones que trae por defecto y agregar las de la tabla de abajo. Luego, en *Projects*, asignarle `pms-hotel-boutique-backend`.
4. **Token:** *My Account → Security → Generate Tokens*:
   - Type: *Project Analysis Token*
   - Project: `pms-hotel-boutique-backend`
   - Copiar el valor en `SONAR_TOKEN`.

### Datos del proyecto

| Dato | Valor |
| --- | --- |
| Repo | `Cesar02xX/pms-hotel-boutique-backend` |
| projectKey | `pms-hotel-boutique-backend` |
| Quality gate | `Aurora Backend` |
| Token (nombre) | `ci-pms-hotel-boutique-backend` |

### Análisis local

```bash
docker compose up -d                      # PostgreSQL requerido por los tests
./mvnw clean verify sonar:sonar \
  -Dsonar.host.url="$SONAR_HOST_URL" \
  -Dsonar.token="$SONAR_TOKEN" \
  -Dsonar.projectKey="$SONAR_PROJECT_KEY" \
  -Dsonar.qualitygate.wait=true
```

### Exponer SonarQube a GitHub Actions

Los runners de GitHub corren en la nube y **no pueden acceder a `http://localhost:9000`**. Para que el análisis corra en los PR, `SONAR_HOST_URL` debe ser alcanzable desde internet. Opciones:

- desplegar el mismo `docker-compose.sonarqube.yml` en un VPS o servidor del equipo con HTTPS;
- usar un túnel temporal (Cloudflare Tunnel o ngrok) hacia `localhost:9000`;
- usar un *self-hosted runner* de GitHub en la misma red que SonarQube.

Mientras no exista una URL alcanzable, el workflow deja un aviso ("SonarQube omitido") y el PR sigue verde. Jenkins on-premise sí puede usar la URL interna.

> **SonarQube Community Build** analiza una sola rama: no tiene análisis por PR ni decoración de PR. Cada análisis actualiza el proyecto, y el quality gate evalúa el código nuevo respecto de la versión/período configurado en *Project Settings → New Code*. Para análisis por rama/PR se necesita Developer Edition o superior.

## Quality gate `Aurora Backend`

Todas las condiciones se evalúan sobre **código nuevo**:

| Condición | Métrica SonarQube | Falla si |
| --- | --- | --- |
| 0 bugs nuevos | `new_bugs` | > 0 |
| 0 vulnerabilidades nuevas | `new_vulnerabilities` | > 0 |
| Rating A en code smells (mantenibilidad) | `new_maintainability_rating` | peor que A |
| Coverage ≥ 70 % | `new_coverage` | < 70 % |
| Duplicación ≤ 3 % | `new_duplicated_lines_density` | > 3 % |

En instancias en modo *Multi-Quality Rule (MQR)*, el script usa automáticamente las métricas equivalentes (`new_software_quality_*`).

Los pipelines ejecutan el análisis con `-Dsonar.qualitygate.wait=true`: si el gate falla, falla el job.

### Coverage (JaCoCo)

- `jacoco-maven-plugin` está en el `pom.xml`. `prepare-agent` instrumenta los tests y `report` corre en la fase `verify`.
- Reporte HTML: `target/site/jacoco/index.html`. XML para SonarQube: `target/site/jacoco/jacoco.xml`.
- Excluidos del cálculo de coverage, por no tener lógica: `**/dto/**`, `**/model/**` y `PmsBackendApplication.java`.

## Producción: tag, release y `.jar`

En cada push a `main`:

1. Se lee `<version>` del `pom.xml` y se quita `-SNAPSHOT` (`0.0.1-SNAPSHOT` → `0.0.1`).
2. Si el tag `v0.0.1` ya existe, se incrementa el patch (`v0.0.2`, `v0.0.3`…) hasta encontrar uno libre.
3. Se empaqueta el `.jar` con esa versión (`versions:set` solo en el runner, sin commitear el `pom.xml`).
4. Se crea el tag y el **GitHub Release** con notas autogeneradas y `pms-backend-X.Y.Z.jar` adjunto.

Para subir versión minor o major, actualizar `<version>` en el `pom.xml` (por ejemplo `0.2.0-SNAPSHOT`) en un PR normal. También se puede publicar una versión exacta empujando un tag: `git tag v1.0.0 && git push origin v1.0.0`.

## Docker / GHCR

- En los PR, la imagen solo se construye (`push: false`) para validar el `Dockerfile`. No requiere permisos ni secrets.
- En `main` o tags `v*`, se publica en `ghcr.io/cesar02xx/pms-hotel-boutique-backend` con los tags `X.Y.Z` y `latest`. GHCR exige el nombre en minúsculas.
- La autenticación usa `GITHUB_TOKEN` con `packages: write`.
- La primera publicación crea el paquete como **privado**. Para hacerlo público o dar acceso: *GitHub → Packages → pms-hotel-boutique-backend → Package settings*.

```bash
docker pull ghcr.io/cesar02xx/pms-hotel-boutique-backend:latest
```

## Deploy (placeholder)

El job `deploy` corre después de publicar la imagen y **se omite** si faltan `DEPLOY_HOST` o `DEPLOY_KEY`. Si están configurados, se conecta por SSH y ejecuta:

```bash
cd "$DEPLOY_PATH" && BACKEND_IMAGE=ghcr.io/cesar02xx/pms-hotel-boutique-backend:X.Y.Z docker compose pull && docker compose up -d
```

En el servidor, `$DEPLOY_PATH/docker-compose.yml` debe usar `${BACKEND_IMAGE}` como imagen del backend y definir las variables de producción: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, etc. El servidor también debe tener `docker login ghcr.io` si el paquete es privado. Nada de esto se versiona en el repo.

Se recomienda crear el environment `production` en GitHub (con aprobadores) y ponerle los secrets `DEPLOY_*` cuando exista infraestructura real.
