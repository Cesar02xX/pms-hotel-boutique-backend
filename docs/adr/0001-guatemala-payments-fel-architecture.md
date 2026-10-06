# ADR 0001: Arquitectura de pagos y FEL para Guatemala

- **Estado:** Aceptado para diseño
- **Alcance:** Demo/académico

## 1. Contexto y problemática regional

La liquidación de pagos en Guatemala requiere considerar la moneda local y el sistema bancario del país. Stripe no se adopta como opción directa para la liquidación local porque no procesa ni liquida directamente fondos en Quetzales guatemaltecos (GTQ) hacia cuentas de bancos locales. Por ello, una implementación regional debe desacoplar el sistema de los proveedores de pago locales y permitir seleccionar el proveedor que corresponda al comercio.

La facturación electrónica en Guatemala (FEL) requiere la intervención de un Certificador DTE autorizado por la SAT. El certificador firma el Documento Tributario Electrónico (DTE), gestiona su certificación y asigna el UUID correspondiente. El sistema no debe asumir una conexión directa contra la SAT para firmar o certificar documentos. Entre los posibles certificadores se consideran Infile, GFACE y Megaprint, sujetos a validación contractual y técnica posterior.

## 2. Arquitectura de pagos locales

La integración de pagos debe mantenerse desacoplada del dominio de reservas y cobros, con soporte futuro para Visanet, NeoNet, QPayPro o Recurrente mediante un proveedor configurable.

El flujo previsto es:

```text
Formulario de pago
        -> Tokenización / 3D Secure
        -> Redirección al proveedor
        -> Webhook asíncrono de confirmación de cobro
```

La confirmación definitiva del cobro debe depender del webhook asíncrono validado del proveedor, no únicamente del retorno del navegador después de la redirección.

## 3. Arquitectura FEL / SAT

La emisión tributaria debe iniciar de forma desacoplada después de confirmar un cobro aprobado. El flujo previsto es:

```text
Evento de cobro aprobado
        -> Generación del DTE (Factura)
        -> Llamada a la API del Certificador DTE
        -> Recepción del UUID/SAT y URL del DTE/PDF
```

La integración se realizará contra la API del Certificador DTE seleccionado. El UUID/SAT y la URL del DTE/PDF serán tratados como datos resultantes de la certificación, sin conexión directa de la aplicación contra la SAT.

## 4. Variables de entorno futuras

Estas variables son únicamente placeholders de configuración futura y no contienen credenciales reales:

### FEL

```text
GUATEMALA_FEL_PROVIDER=
GUATEMALA_FEL_API_KEY=
GUATEMALA_FEL_EFACE_USER=
GUATEMALA_FEL_CERTIFIER_URL=
```

### Pagos

```text
GUATEMALA_PAYMENT_PROVIDER=
GUATEMALA_PAYMENT_MERCHANT_ID=
GUATEMALA_PAYMENT_PUBLIC_KEY=
GUATEMALA_PAYMENT_SECRET_KEY=
GUATEMALA_PAYMENT_WEBHOOK_SECRET=
```

## 5. Fuera de alcance del proyecto

Este proyecto mantiene un alcance estrictamente demo/académico. Quedan fuera de alcance la integración productiva para procesar o liquidar dinero real y la emisión, firma, certificación o almacenamiento de documentos tributarios reales ante la SAT o sus certificadores.

