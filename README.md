# CleanYourTV Plus

Fork de [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv), mantido em [veronezz/cleanyourtv-](https://github.com/veronezz/cleanyourtv-).

Esta branch reúne o launcher **3.19.0-unitv** (`versionCode 37`), pacote `com.gathin.tvhome`, para Android 8.0 ou superior. A interface é voltada a controle remoto, com suporte adicional a tablets e projetores.

## Recursos

- Grade e dock configuráveis; mover, renomear e trocar imagens pelo menu ao segurar OK.
- Clima com cidade manual e papel de parede personalizável.
- Packs de ícones no formato appfilter, escolha manual de imagens e cor individual dos cartões.
- Preferências agrupadas e seletor único de linhas de conteúdo.
- Linha própria do UniTV com histórico e séries acompanhadas, quando uma ponte compatível está instalada.
- Linhas de continuar assistindo conforme os dados publicados pelos aplicativos e as permissões disponíveis.
- Painel Spotify com capa, controles e progresso fornecido pela sessão de mídia.
- Alternativa opcional por sessões de mídia em dispositivos sem TV Provider.
- Modo projetor com atalhos para apps ou activities, seleção agrupada por aplicativo e ícones vetoriais para funções.
- Rolagem por controle remoto, opção de movimento reduzido e espaço independente abaixo do relógio.

## Limitações das integrações

O launcher não lê bancos privados de outros aplicativos nem inclui contas de streaming. Cada fonte depende dos dados que o aplicativo publica. A alternativa por sessões de mídia exige autorização e seleção explícita dos apps; não recupera um histórico completo e pode abrir apenas a tela principal do aplicativo.

O UniTV não acompanha este repositório. A linha própria exige a ponte `com.global.unitviptv.homebridge`. Sem ela, o launcher continua utilizável. A ponte atual não informa posição/duração: o filtro automático de cinco minutos não se aplica a esses cards. Packs de ícones também são instalados separadamente; nenhuma arte de terceiros do pack é incluída.

Atalhos de projetor só podem abrir activities habilitadas, exportadas e acessíveis às permissões do launcher. Não há compatibilidade universal com todas as funções dos fabricantes.

## Compilar

O projeto Android está em [`launcher/`](launcher/). Consulte [COMPILAR.md](launcher/COMPILAR.md) para Windows, Linux e WSL.

Requisitos: JDK 17, Android SDK 34, Build Tools 34.0.0. O Gradle Wrapper está incluído.

Para verificar o código e produzir um APK **sem assinatura**, sem precisar da chave do mantenedor:

```bash
cd launcher
./gradlew -PciUnsignedRelease=true :app:testReleaseUnitTest :app:assembleRelease
```

Esse APK precisa ser assinado antes de instalar. Para gerar atualizações compatíveis com a instalação personalizada existente, use a chave original e os scripts `build-apk.sh` ou `build-apk.ps1`. A chave privada não acompanha o repositório. Uma chave diferente não atualiza uma instalação anterior e pode não ser aceita pela ponte UniTV.

## Validação

A revisão 3.19.0 entregue foi compilada a partir de um ZIP limpo e passou em **168 testes JVM**. O relatório está em [REVISAO-v19.md](launcher/REVISAO-v19.md). Esses testes não substituem a validação de foco, standby, retomada e funções específicas no dispositivo. O fluxo de Actions deste fork compila sem a chave privada e executa os testes; seus resultados devem ser consultados na aba Actions.

## Histórico e contribuições

Esta branch preserva o histórico do fork e importa a versão personalizada como uma alteração própria. A revisão comparou seletivamente o upstream v3.0.0, commit `67dc5bee169038ed065a48ef837cf7fed5cf40f4`; não incorpora indiscriminadamente todas as mudanças posteriores do projeto original.

A [documentação original](docs/README-upstream.md), ferramentas e exemplos de dispositivos permanecem disponíveis como referência. Seus procedimentos de alteração do sistema são independentes da instalação do launcher.

Para contribuir nesta edição, leia [CONTRIBUTING-FORK.md](CONTRIBUTING-FORK.md).

## Licenças e créditos

Código sob a licença [MIT](LICENSE), com créditos preservados ao projeto original. A fonte incluída tem sua licença em [FONT-LICENSE-OFL.txt](launcher/FONT-LICENSE-OFL.txt). As marcas dos aplicativos pertencem aos respectivos titulares.
