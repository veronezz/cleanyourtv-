# CleanYourTV 3.19.0 — revisão da base

A revisão parte da versão 3.18.0 que você aprovou. A referência externa foi a tag **v3.0.0**, commit **67dc5bee169038ed065a48ef837cf7fed5cf40f4**, do [CleanYourTV original](https://github.com/Gathin23/cleanyourtv/releases/tag/v3.0.0). O código dessa tag foi baixado e comparado com o nosso; a branch main não foi usada como base desta atualização.

## Limpeza aplicada

- **Apps visíveis:** removida a entrada separada “Aplicativos ocultos”. Mostrar e ocultar ficam em **Aplicativos → Apps na tela inicial**. As chaves de preferências existentes continuam válidas; a escolha do usuário e os defaults do tema precisam continuar distinguíveis.
- **Imagem de fundo:** removido o segundo botão que abria a galeria. A escolha permanece em **Planos de fundo → Fonte do fundo → Imagem da galeria**.
- **Autorizações:** os dois acessos separados foram concentrados em **Mídia e sistema → Permissões de mídia**, com programação de TV e sessões de reprodução. Os botões de autorização dentro de Mídias recentes continuam como atalhos para a mesma tela do Android; usam a mesma rotina, sem configurações independentes.
- Removidos `rowSwitch`, o diálogo antigo de ocultos e seu helper de rótulos, o cálculo antigo `headerDp`, uma variável sem uso e o campo `hidden` do adapter que era escrito sem ser consultado.
- A construção das preferências foi dividida em métodos por seção. Não foram criados novos tipos de configuração nem alterados formatos dos dados salvos.
- A limpeza de rótulos ficou numa rotina comum (`DisplayLabel`): sessões de mídia deixam de depender da classe de armazenamento de atalhos do projetor.
- O carregamento dos mapas de teclas foi unificado para os dois serviços existentes; arrays de teclas deixam de ser carregados a cada evento.
- As expressões de validação do leitor de packs e de rótulos são compiladas uma vez. As regras de correspondência permanecem iguais.
- Classes adicionadas nas últimas versões receberam formatação consistente. Linhas maiores foram abertas em blocos legíveis; isso é manutenção, não uma promessa de desempenho.
- Textos promocionais foram trocados por “Tela inicial” e pelo crédito ao projeto original. O link de crédito agora aponta para o repositório correto.
- O seletor de apps das mídias recentes fecha o indicador de carregamento e informa a indisponibilidade caso o firmware falhe na consulta, em vez de deixar o diálogo aguardando.

## O que incorporar da 3.0 original

| Área | Decisão nesta versão | Motivo |
|---|---|---|
| Política de memória (`MemoryTrimPolicy`) | Incorporada | Preserva os recursos visuais enquanto Home está iniciado e mantém o retorno normal sem limpeza por `UI_HIDDEN`. A limpeza existente continua disponível sob pressão de memória em segundo plano. |
| Limpeza profunda: esvaziar adapter, desacoplar grid e reconstruir no retorno | Adiada | Depende do protocolo `TileRefresh` do original. Portar só metade poderia comprometer o retorno e o foco que você validou. |
| Informações do app (`SystemSettings.appInfo`) | Incorporada | A ação só aparece se o sistema oferece uma tela capaz de atendê-la. |
| Auxiliar de controle em espera | Adaptado parcialmente | Os dois serviços deixam as teclas passar com a tela não interativa. Não é o mecanismo completo do original que desativa/reativa serviços; não há nova opção para configurar. Precisa de teste físico do controle. |
| Navegação da grade, VPN, isolamento dos módulos e activity de entradas | Já equivalentes em arquivos específicos | `GridNavigation`, `VpnBadge`, `CardFeature` e `InputsRequestActivity` eram idênticos à tag. Não havia correção a reaplicar nesses arquivos. |
| Biblioteca de apps e escolhas manuais | Manter nossa implementação | O original também tem `LibraryApps`/`AppLibraryDialog`, com outro armazenamento. Substituir o nosso seletor duplicaria a migração de uma função já validada. |
| Settings do fabricante | Manter nossa verificação | Nosso fallback já confere a activity e se o app está habilitado; a tag original confere apenas o pacote nessa parte. Não faria sentido enfraquecer a verificação. |
| Grade única horizontal e novos controles de dock | Não incorporar agora | São escolhas de layout, não correções necessárias à grade e às linhas de conteúdo atuais. |
| Ícones uniformes do original | Não incorporar como uma segunda opção paralela | Nossa base já oferece pack, imagem manual e cor por cartão. Uma opção global de outro modelo aumentaria a sobreposição de controles. |
| `power_on_home`, redirecionamento do launcher original e reativação de serviços | Adiar até existir necessidade no firmware | Mudam inicialização, standby ou o estado de serviços; não resolvem um problema demonstrado nesta TV. |
| Remapeamento de volume, ações extras do controle e passthrough de gamepad | Adiar | Dependem do controle/firmware e de um caso de uso concreto. O remapeamento atual foi preservado. |
| Relógios adicionais, data lunar, efeitos sazonais, temas e mascote | Fora desta revisão | Funcionalidades novas sem relação com a limpeza solicitada. |
| Add-ons, Home Assistant, bloqueio de anúncios, acesso remoto e branding no boot | Fora desta revisão | Integrações opcionais com dependências próprias. Não entram no núcleo apenas para acompanhar a lista de recursos do original. |
| Scripts/listas para outros modelos de TV | Fora do APK | São procedimentos de instalação e configuração de aparelhos. Esta revisão modifica o launcher, sem aplicar listas de desativação na TV. |

A maior parte da 3.0 é expansão de recursos e suporte a outros aparelhos. Não é um pacote de correções que deva substituir integralmente `MainActivity`, `AppAdapter` e `Config` da nossa versão.

## O que da nossa versão faria sentido levar ao projeto original

Isso é uma recomendação técnica; nenhuma contribuição foi enviada.

| Recurso | Recomendação |
|---|---|
| Leitor de packs `appfilter` e seleção manual de imagens | Bom candidato a mudança isolada. Precisa integrar ao sistema de temas do original e respeitar o orçamento de tamanho do APK. |
| Cores individuais dos cartões transparentes | Bom candidato junto da personalização de imagens, evitando competir com as opções globais de tema do original. |
| Seletor de activities por app e símbolos do projetor | Candidato opcional, com os filtros de acesso e sem presets que suponham que toda marca usa a mesma activity. |
| Descoberta de apps comuns e seleção manual | Comparar com a biblioteca já existente no original; não apresentar como função inédita nem introduzir dois armazenamentos. |
| Spotify e fallback por sessões | Avaliar como integração optativa. Precisa explicar a autorização do Android, o custo dos observadores e os limites de retomada, sem prometer histórico completo. |
| Ponte de histórico e séries do UniTV | Manter específica desta personalização. Depende de um pacote e de uma versão modificada do UniTV; não é uma solução genérica para o núcleo do launcher. |

## O que permaneceu como contrato de compatibilidade

Grade, espaçamento abaixo do relógio, alinhamento dos botões, painel Spotify, cores, ícones, pack, atalhos do projetor, linhas e regras de histórico mantêm suas configurações. O manifesto e os layouts principais são idênticos aos da 3.18.0. Nenhuma permissão foi acrescentada. Migrações antigas foram mantidas porque podem ser necessárias ao instalar sobre versões anteriores.

As capturas de exceções para recursos opcionais, firmware de terceiros e packs incompletos não foram removidas indiscriminadamente: várias são caminhos de fallback necessários, e não código sem finalidade. A revisão não pretende identificar a autoria humana/IA de cada linha; procura duplicação, estado sem efeito, acoplamento desnecessário e comportamento verificável.

## Validação e limites

O APK é gerado de uma extração nova do ZIP de fontes. Os testes JVM incluem os três casos de memória trazidos do original, além das verificações existentes de packs, atalhos, cores, histórico e rolagem. Assinatura e alinhamento são verificados antes da entrega. Os resultados e hashes estão em `validacao-launcher-v19.json`, também dentro do ZIP.

Não houve medição de FPS, RAM ou tempo de abertura na TV. Remover helpers mortos pode não reduzir o APK, porque o R8 já os eliminava. A política de memória e as teclas em espera ainda precisam de teste no aparelho; o comportamento físico do controle não é reproduzido pelos testes JVM.

## Instalação

Atualize por cima sem limpar dados. A assinatura é a mesma das versões anteriores; mantenha a chave original separada do ZIP. Não é necessário atualizar o UniTV.

```powershell
$adb = "$env:USERPROFILE\Desktop\platform-tools\adb.exe"
$tv = "IP_DA_TV:PORTA_ADB"
& $adb connect $tv
& $adb -s $tv install -r "$env:USERPROFILE\Downloads\CleanYourTV-3.19.0-Revisao.apk"
```

Ajuste `$tv` se a conexão ADB tiver mudado. Para compilar, siga `COMPILAR.md` (JDK 17 e SDK Android 34). O pack continua instalado separadamente.

## Referências de código

- [Política de memória da tag](https://github.com/Gathin23/cleanyourtv/blob/67dc5bee169038ed065a48ef837cf7fed5cf40f4/launcher/app/src/main/java/com/gathin/tvhome/MemoryTrimPolicy.java)
- [Ciclo de memória e retorno do original](https://github.com/Gathin23/cleanyourtv/blob/67dc5bee169038ed065a48ef837cf7fed5cf40f4/launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java)
- [Settings e App info](https://github.com/Gathin23/cleanyourtv/blob/67dc5bee169038ed065a48ef837cf7fed5cf40f4/launcher/app/src/main/java/com/gathin/tvhome/SystemSettings.java)
- [Serviço Home e standby do original](https://github.com/Gathin23/cleanyourtv/blob/67dc5bee169038ed065a48ef837cf7fed5cf40f4/launcher/app/src/main/java/com/gathin/tvhome/HomeKeyService.java)
- [Escolhas da biblioteca do original](https://github.com/Gathin23/cleanyourtv/blob/67dc5bee169038ed065a48ef837cf7fed5cf40f4/launcher/app/src/main/java/com/gathin/tvhome/LibraryApps.java)
