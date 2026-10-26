# CleanYourTV 3.18.0

## Funções do projetor

Adicionar função/activity agora começa pela lista de aplicativos. Cada app mostra quantas telas acessíveis oferece. Ao selecionar um app, aparecem somente suas activities; Voltar retorna aos aplicativos, preservando a busca e a posição da lista. A opção Abrir aplicativo continua disponível separadamente.

No editor do atalho, escolha Ícone: original do app, Mídias USB, Ajuste de foco, Enquadramento de imagem ou Brilho. Os quatro novos símbolos são vetoriais. A escolha só é gravada ao salvar o editor; cancelar não muda o ícone. Atalhos antigos continuam usando o ícone original. Segurar OK em um atalho também oferece Ícone do atalho e Cor de fundo.

## Cor dos cartões

Segure OK no aplicativo → Trocar ícone ou imagem → Cor de fundo do cartão. Escolha uma cor da paleta, Sem fundo, Padrão do launcher ou informe #RRGGBB em Cor personalizada. A cor é individual por app e não modifica a imagem do pack. Banners opacos cobrem a cor; ela aparece atrás de áreas transparentes e ícones. Atalhos do projetor guardam a cor por activity, independentemente dos outros atalhos do mesmo app. Os símbolos vetoriais e o texto recebem contraste escuro em fundos claros.

## Spotify e cabeçalho

A rolagem passa a enquadrar todo o painel do Spotify ao focar a capa ou seus controles, em vez de revelar só o botão selecionado. Mantém a arte ao fundo, o card no canto inferior esquerdo e os controles de faixa/progresso. Também reconhece sessões de com.spotify.music, além de com.spotify.tv.android. Wi-Fi, Preferências e Entradas sobem juntos para o topo direito, alinhados ao topo da saudação. O espaçamento padrão de uma linha abaixo do relógio foi preservado.

## Celulares e projetores sem serviço de TV

READ_TV_LISTINGS é definida pelo provedor de programação de TV. O launcher agora identifica a disponibilidade do provedor e da permissão, em vez de assumir que todo dispositivo tem esse serviço ou que todo projetor é Android TV. Sem serviço de TV não há como obter por essa permissão um Watch Next, guia ou recomendações que não existem no sistema.

Preferências → Mídia e sistema → Compatibilidade e permissões de mídia mostra a condição detectada. O diagnóstico também informa o acesso às sessões. A ponte UniTV continua independente do serviço de TV.

Como alternativa opcional:

1. Ative a linha Continuar assistindo em Linhas e cartões.
2. Abra Mídias recentes · celular/projetor e ative a captura.
3. Em Escolher apps, selecione os aplicativos desejados.
4. Em Autorizar acesso às sessões de mídia, habilite o serviço do launcher na tela do Android.
5. Reproduza algo nesses apps e retorne ao launcher.

A alternativa guarda a última sessão de cada app escolhido (até 32 apps), com título, posição/duração e imagem quando publicados pelo app. Ela complementa a linha Continuar assistindo, priorizando os cartões reais do TV Provider e evitando duplicar apps que já os publicam. UniTV usa a ponte própria e não entra nessa captura. Não lê mensagens de notificações nem bancos privados dos apps.

O cartão usa a ação de abertura da sessão quando ainda disponível; caso contrário abre o app, para retomar dentro dele. Não garante abrir o episódio exato e não recupera o histórico completo. Alguns apps não publicam sessões/metadados; nesses casos não há cartão a criar. Não produz listas de recomendações ou guia ao vivo para apps que não publicam esses dados.

Remover por toque longo e a regra opcional dos cinco minutos continuam aplicáveis quando há posição/duração. Limpar e desativar captura apaga este cache local e desliga a captura. As configurações de outros apps não são alteradas.

Referências: https://android.googlesource.com/platform/packages/providers/TvProvider/+/a92fd400301d4aaa0f02754085b1fab8b9095fcc/AndroidManifest.xml e https://developer.android.com/reference/android/media/session/MediaSessionManager

## Compilar

Use COMPILAR.md e sua chave original, mantida fora deste ZIP. JDK 17 e SDK Android 34 seguem sendo os requisitos. O pack de ícones é instalado separadamente, como na versão 3.17.0.
