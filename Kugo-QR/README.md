# 📱 Kugo QR

Leitor de QR Code para Android, desenvolvido em Kotlin.

## ✨ Recursos
- 📷 Leitura de QR Code pela câmera em tempo real.
- 🖼️ Leitura de QR Code em imagens da galeria.
- 📋 Copiar o conteúdo encontrado.
- 🌐 Abrir links HTTP/HTTPS encontrados.
- 🔒 A galeria usa o seletor de imagens do Android; não precisa de permissão ampla de armazenamento.

## 🧰 Tecnologias
- Kotlin
- AndroidX CameraX
- Google ML Kit Barcode Scanning
- Material Components

## 🏗️ Compilar
Requisitos: JDK 17, Android SDK e Gradle 8.9.

```bash
gradle assembleDebug
```

APK gerado em:
`app/build/outputs/apk/debug/app-debug.apk`

## 🤖 GitHub Actions
O workflow em `.github/workflows/android.yml` compila o app em pushes para `main`, permite execução manual e publica uma Release quando uma tag `v*` é enviada.

## 📄 Licença
MIT
