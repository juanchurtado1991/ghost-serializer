# Documentación Ghost Serialization

| Archivo | Idioma | Uso |
|---------|--------|-----|
| [GHOST_MANUAL_ES.md](GHOST_MANUAL_ES.md) | **Español** | Manual canónico (fuente del PDF) |
| [GHOST_MANUAL_EN.md](GHOST_MANUAL_EN.md) | Inglés | Copia opcional |
| [Ghost-Serialization-Manual-1.1.17.pdf](Ghost-Serialization-Manual-1.1.17.pdf) | Español | PDF A5 para móvil |

Regenerar el PDF:

```bash
.venv-pdf/bin/python scripts/build_ghost_manual_pdf.py
```

Actualizar conteo de tests:

```bash
./gradlew ciTestJvm
```
