# Gerar o APK deste projeto

Este é o projeto Gradle completo do launcher `com.gathin.tvhome`. Contém fontes, recursos, testes, Gradle Wrapper e scripts de compilação. A chave privada permanece com você.

## Preparar

- Extraia o ZIP em uma pasta nova.
- Instale/use um JDK 17 completo e deixe `java` e `keytool` no PATH.
- Tenha o Android SDK com a plataforma Android 34 e Build Tools 34.0.0. O Gradle usa `ANDROID_HOME`, `ANDROID_SDK_ROOT` ou seu `local.properties` local.
- Na primeira compilação, o Gradle baixa a distribuição e dependências dos repositórios oficiais, quando ainda não estiverem em cache.
- Use a `debug.keystore` original enviada nesta conversa. Você pode copiá-la para `signing/debug.keystore` ou indicar seu caminho ao script. Ela não acompanha o ZIP.

Os scripts verificam o certificado público esperado, pois ele precisa continuar compatível com as atualizações instaladas e com a ponte do UniTV. A assinatura debug mantém o alias e as credenciais padrão da chave que você forneceu. Não gere outra chave para substituir a original.

## Windows PowerShell

Abra o PowerShell na pasta que contém `gradlew.bat` e `build-apk.ps1`:

```powershell
.\build-apk.ps1 -KeyStore "C:\caminho\debug.keystore" -SdkPath "C:\Android\Sdk"
```

Se a política local bloquear a execução do arquivo `.ps1`, execute apenas essa chamada com a política definida para o processo:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-apk.ps1 -KeyStore "C:\caminho\debug.keystore" -SdkPath "C:\Android\Sdk"
```

Isso não altera a política global do Windows. Você também pode usar Android Studio: abra esta pasta como projeto, configure Java 17/SDK 34 e coloque a chave em `signing/debug.keystore` antes de gerar a variante release.

## WSL / Ubuntu / Linux

Use JDK e SDK Linux dentro do WSL. Na pasta que contém `gradlew`:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
chmod +x build-apk.sh gradlew
./build-apk.sh "/caminho/debug.keystore"
```

A variável `UNITV_LAUNCHER_KEY` também pode apontar para a chave. O primeiro argumento do script tem prioridade. Os SDKs para Windows e Linux usam ferramentas nativas diferentes.

## Resultado

Os scripts executam `:app:assembleRelease` e `:app:testReleaseUnitTest`. Se alguma etapa falhar, param sem anunciar sucesso.

APK copiado para:

```text
dist/CleanYourTV.apk
```

O arquivo original também fica em `app/build/outputs/apk/release/app-release.apk`. Os resultados dos testes ficam em `app/build/reports/tests/testReleaseUnitTest/`.

## Manter o projeto

- Preserve o pacote e a chave para instalar atualizações com `adb install -r`.
- Aumente `versionCode` em `app/build.gradle` em cada nova versão distribuída.
- Atualize `versionName` conforme a versão que quiser exibir. O nome genérico do arquivo em `dist` não precisa ser alterado.
- A configuração do seu SDK e as chaves não são incluídas no projeto compartilhado.

O script Linux e o Gradle Wrapper são executados na validação desta revisão. O script PowerShell foi preparado para o mesmo fluxo; sua execução no Windows não foi testada neste ambiente Linux.


## Versão 3.16.0

- Apps com entradas Android TV e Android comuns são unidos por pacote, preferindo a entrada de TV.
- Preferências → Aplicativos → Apps na tela inicial permite escolher o que aparece.
- O espaço abaixo do relógio volta a uma linha de apps por padrão, independente da compactação das linhas.
- Preferências → Projetor → Modo projetor libera os menus para adicionar funções e apps. Os atalhos ficam na grade e podem ser movidos para a dock.
- Na busca, use o nome do fabricante/app ou termos como imagem, display, keystone ou projection. Escolha uma tela, use Testar e depois Salvar. Segure OK no atalho para mover, renomear, testar ou remover.
- Ao desligar o modo projetor, seus atalhos desaparecem da grade, mas continuam salvos para a próxima ativação.
- O seletor de funções consulta os pacotes instalados localmente, inclusive apps do fabricante sem entrada de launcher (QUERY_ALL_PACKAGES). Não envia essa lista a um servidor.
- O launcher só abre activities habilitadas, exportadas e sem permissões inacessíveis. Funções privadas, que exigem privilégios de sistema ou parâmetros específicos podem não funcionar. Não há suporte universal garantido a cada marca de projetor.
- Compilação e testes JVM foram verificados neste ambiente. Interface e funções específicas precisam de validação no tablet/TV/projetor.

Ao adicionar atalho do projetor, escolha **Abrir aplicativo** (entrada principal Android TV/Android) ou **Abrir função / activity** (tela específica exportada). Ambos permitem Testar, nomear e Salvar.


## Versão 3.17.0

Ícones vetoriais próprios para Aparência, Linhas, Projetor e Mídia/sistema; Apps, Clima e Plano de fundo mantidos. Suporte a packs instalados com appfilter, incluindo o formato do Projectivy Icon Pack. Consulte ICON_PACKS.md. O pack e seus recursos não acompanham o ZIP.


## Versão 3.18.0

Activities agrupadas por app, símbolos vetoriais para funções do projetor, cor individual de cartões, enquadramento do painel Spotify, cabeçalho alinhado ao topo e alternativa opcional por sessões para dispositivos sem TV Provider. Consulte REFINAMENTOS-v18.md.


## Versão 3.19.0

Revisão de código e consolidação de preferências. Integração seletiva de correções da tag v3.0.0 (67dc5be). Veja REVISAO-v19.md para decisões, compatibilidade e validação.

## Verificar um clone público sem a chave original

```bash
./gradlew -PciUnsignedRelease=true :app:testReleaseUnitTest :app:assembleRelease
```

Essa opção deixa apenas a variante release sem assinatura. A saída é `app/build/outputs/apk/release/app-release-unsigned.apk`; não pode ser instalada antes de ser assinada. Os scripts `build-apk` continuam exigindo e verificando a chave original. Não coloque chaves no Git.
