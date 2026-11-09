# 📱 Kugo QR

**Escaneie. Acesse. Rápido.**

Leitor de QR Code para Android com interface renovada e câmera em tela cheia.

## ✨ Novidades da versão 1.1.0
- 🎨 Interface redesenhada com visual moderno, escuro e detalhes em verde.
- 📷 Pré-visualização da câmera ocupando toda a tela.
- ⚡ Leitura em toda a imagem da câmera: não existe quadrado obrigatório nem necessidade de alinhar o código no centro.
- 🔦 Botão para ligar e desligar o flash (quando disponível).
- 🖼️ Leitura de QR Code em imagens da galeria.
- 📋 Copiar o conteúdo encontrado.
- 🌐 Abrir links HTTP/HTTPS.
- 🟢 Novo ícone do aplicativo.

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

## 🤖 GitHub Actions
O workflow em `.github/workflows/android.yml` compila o app em pushes para `main`, permite execução manual e publica uma Release quando uma tag `v*` é enviada.

## 📄 Licença
MIT
