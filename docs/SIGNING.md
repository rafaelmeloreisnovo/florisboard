# Assinatura de APKs do FlorisBoard

Este documento descreve como configurar e usar o sistema de assinatura de APKs do FlorisBoard.

## Visão Geral

O projeto inclui um script automatizado para assinar APKs usando o `apksigner` do Android SDK. O script lê as configurações de um arquivo `signing.properties` que contém as informações do keystore.

## Arquivos

- **`signing.properties.template`**: Template com as variáveis necessárias para configuração
- **`sign_apk.sh`**: Script bash que realiza a assinatura do APK
- **`build_and_sign.sh`**: Script integrado que faz build e assinatura em uma única execução
- **`signing.properties`**: Arquivo de configuração real (não versionado, criado pelo usuário)

## Configuração Inicial

### 1. Criar o Arquivo de Configuração

Copie o template para criar seu arquivo de configuração:

```bash
cp signing.properties.template signing.properties
```

### 2. Preencher as Variáveis

Edite o arquivo `signing.properties` com suas informações de keystore:

```properties
# Caminho para o arquivo keystore (.jks ou .keystore)
KEYSTORE_FILE=caminho/para/seu/keystore.jks

# Alias da chave no keystore
KEYSTORE_ALIAS=seu_alias

# Senha do keystore
KEYSTORE_PASSWORD=sua_senha_keystore

# Senha do alias (frequentemente a mesma do keystore)
KEY_PASSWORD=sua_senha_alias
```

**Importante:** O arquivo `signing.properties` está incluído no `.gitignore` e nunca deve ser commitado ao repositório por questões de segurança.

### 3. Criar um Keystore (se necessário)

Se você ainda não possui um keystore, pode criar um usando o `keytool`:

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias florisboard_release
```

Siga as instruções interativas para configurar o keystore.

## Uso

### Assinar um APK

Para assinar um APK, execute o script passando o caminho do APK como argumento:

```bash
./sign_apk.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

### Fluxo Completo de Build e Assinatura

#### Opção 1: Build e Assinatura Automatizado (Recomendado)

Use o script integrado que faz build e assinatura em um único comando:

```bash
./build_and_sign.sh
```

Este script executa automaticamente:
1. Limpeza dos builds anteriores
2. Build do APK release não assinado
3. Assinatura do APK gerado
4. Verificação da assinatura
5. Geração do checksum SHA256

#### Opção 2: Build e Assinatura Manual

```bash
# 1. Build do APK não assinado
./gradlew :app:assembleRelease

# 2. Assinar o APK
./sign_apk.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

#### Opção 3: Usando o Script de Build Existente

O projeto já possui um script `build_unsigned.sh` para builds não assinados:

```bash
# Build sem assinatura
./build_unsigned.sh

# Depois assine o APK gerado
./sign_apk.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

## O que o Script Faz

O script `sign_apk.sh` realiza as seguintes operações:

1. **Validação**: Verifica se todos os parâmetros e arquivos necessários existem
2. **Localização do apksigner**: Encontra automaticamente o `apksigner` no Android SDK
3. **Assinatura**: Assina o APK usando as credenciais do `signing.properties`
4. **Verificação**: Verifica a assinatura do APK gerado
5. **Checksum**: Gera um arquivo SHA256 para verificação de integridade
6. **Output**: Gera um arquivo `*-signed.apk` no mesmo diretório do APK original

## Saída

Após a execução bem-sucedida, o script gera:

- **APK Assinado**: `<nome-original>-signed.apk`
- **Checksum SHA256**: `<nome-original>-signed.apk.sha256`

Exemplo:
```
app-release-unsigned-signed.apk
app-release-unsigned-signed.apk.sha256
```

## Requisitos

- **Android SDK Build Tools**: O `apksigner` deve estar instalado
- **Variável ANDROID_HOME**: Deve estar configurada apontando para o Android SDK
- **Keystore válido**: Arquivo .jks ou .keystore configurado

## Segurança

⚠️ **IMPORTANTE**: 

- Nunca commite o arquivo `signing.properties`
- Mantenha seu keystore em local seguro
- Faça backup do keystore e das senhas
- Não compartilhe suas credenciais de assinatura

O arquivo `signing.properties` está listado no `.gitignore` para evitar commits acidentais.

## Integração com CI/CD

Para usar em ambientes de CI/CD, você pode criar o `signing.properties` dinamicamente usando variáveis de ambiente:

```bash
cat > signing.properties <<EOF
KEYSTORE_FILE=${CI_KEYSTORE_PATH}
KEYSTORE_ALIAS=${CI_KEYSTORE_ALIAS}
KEYSTORE_PASSWORD=${CI_KEYSTORE_PASSWORD}
KEY_PASSWORD=${CI_KEY_PASSWORD}
EOF

./sign_apk.sh path/to/apk
```

## Troubleshooting

### "apksigner not found"

Certifique-se de que o Android SDK Build Tools está instalado e que a variável `ANDROID_HOME` está configurada:

```bash
export ANDROID_HOME=/path/to/android/sdk
```

### "Keystore file not found"

Verifique se o caminho do keystore em `signing.properties` está correto. Caminhos relativos são resolvidos a partir do diretório raiz do projeto.

### "Wrong password"

Verifique se as senhas em `signing.properties` estão corretas. Teste com:

```bash
keytool -list -v -keystore seu_keystore.jks
```

## Referências

- [Documentação oficial do apksigner](https://developer.android.com/studio/command-line/apksigner)
- [Guia de assinatura de apps Android](https://developer.android.com/studio/publish/app-signing)
