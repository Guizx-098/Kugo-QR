# 📱 Kugo QR

**Escaneie. Acesse. Rápido.**

Leitor de QR Code para Android com leitura automática pela câmera e interface minimalista.

## ✨ Novidades da versão 1.3.0
- 🎨 Interface mais limpa, compacta e sem áreas vazias exageradas.
- 📷 Câmera em destaque, com degradê superior mais discreto.
- 📱 Ajuste das barras de status e navegação para evitar sobreposição.
- 🧾 Resultado aparece somente quando um QR Code é identificado.
- 🔦 Controle de flash quando disponível.
- 🖼️ Leitura de QR Code pela galeria.
- 📋 Copiar o conteúdo encontrado.
- 🌐 Abrir links HTTP/HTTPS.

## 🧰 Tecnologias
- Kotlin
- AndroidX CameraX
- Google ML Kit Barcode Scanning
- Material Components 3

## 🏗️ Compilar
Requisitos: JDK 17, Android SDK e Gradle 8.9.

```bash
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## 🤖 GitHub Actions e Releases
O workflow compila o APK em pushes para `main` e também pode ser iniciado manualmente. Ao publicar uma tag `v*`, o APK é anexado automaticamente à GitHub Release e disponibilizado como artefato do Actions.

## 📄 Licença
MIT
