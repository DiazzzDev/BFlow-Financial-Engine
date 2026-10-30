# Publicar el diagrama de arquitectura en GitHub Pages

El diagrama es una página estática y autocontenida. GitHub Pages puede publicarlo directamente desde `docs/architecture/`; no es necesario compilar la aplicación ni desplegar el backend.

## 1. Añadir el workflow de publicación

Crea `.github/workflows/architecture-pages.yml` con este contenido:

```yaml
name: Publish architecture diagram

on:
  push:
    branches:
      - main
    paths:
      - "docs/architecture/**"
      - ".github/workflows/architecture-pages.yml"
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: architecture-pages
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v7

      - name: Configure GitHub Pages
        uses: actions/configure-pages@v5

      - name: Upload architecture site
        uses: actions/upload-pages-artifact@v3
        with:
          path: docs/architecture

  deploy:
    needs: build
    runs-on: ubuntu-latest
    permissions:
      pages: write
      id-token: write
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4
```

El artifact publica el contenido de `docs/architecture` en la raíz del sitio. Así, `index.html` será la página inicial y la carpeta `icons/` también estará disponible.

## 2. Habilitar GitHub Pages

En GitHub, abre el repositorio y ve a **Settings → Pages**. En **Build and deployment**, selecciona **GitHub Actions** como source. No selecciones `Deploy from a branch`: este workflow publica el artifact.

## 3. Publicar

Guarda y sube el workflow a `main`, junto con la versión del diagrama que quieras publicar. También puedes iniciar una ejecución manual desde **Actions → Publish architecture diagram → Run workflow**.

Cuando el job `deploy` termine correctamente, GitHub mostrará la URL en el resumen del workflow y en **Settings → Pages**. Para un sitio de proyecto, tendrá esta forma:

```text
https://<usuario-o-organizacion>.github.io/<nombre-del-repositorio>/
```

La página se sirve desde `index.html`; no uses el enlace de GitHub que termina en `/blob/...`, porque ese muestra el código fuente.

## Usar un dominio propio (opcional)

Es preferible usar un subdominio dedicado, por ejemplo `architecture.bflow-studio.com`. No configures `api.bflow-studio.com`: ese nombre ya identifica al backend.

1. En **Settings → Pages → Custom domain**, escribe `architecture.bflow-studio.com` y guarda.
2. En la zona DNS de `bflow-studio.com`, crea el registro que GitHub Pages indique. Para un subdominio normalmente será un `CNAME` con nombre `architecture` y destino `<usuario-o-organizacion>.github.io`. Si usas Cloudflare, comienza con el proxy desactivado (**DNS only**) mientras verificas el dominio y HTTPS.
3. Añade `docs/architecture/CNAME` con una sola línea:

   ```text
   architecture.bflow-studio.com
   ```

4. Vuelve a subir los cambios a `main`. Comprueba en **Settings → Pages** que el dominio aparece como verificado y activa **Enforce HTTPS** cuando esté disponible.

La propagación DNS y la emisión del certificado pueden tardar. Sigue el valor de destino que muestre GitHub para el repositorio y no cambies el registro A o CNAME del API.

## Actualizaciones y solución de problemas

- Los cambios en `docs/architecture/**` publicados en `main` ejecutan el workflow automáticamente.
- Si la URL devuelve 404, confirma que **Settings → Pages** usa **GitHub Actions**, que el workflow terminó bien y que la URL incluye el nombre del repositorio en sitios de proyecto.
- Si el workflow no se ejecuta, verifica que se subió a `main` y que las rutas modificadas coinciden con el filtro `paths`.
- Si el diagrama no carga sus íconos, confirma que `docs/architecture/icons/` se incluyó en el artifact. `index.html` está diseñado para funcionar sin dependencias externas.
- Para un dominio propio, revisa el estado DNS y el dominio indicado en **Settings → Pages** antes de volver a ejecutar el workflow.
