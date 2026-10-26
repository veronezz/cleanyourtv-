# Pacotes de ícones — CleanYourTV 3.17.0

## Ativar

1. Instale o APK do Projectivy Icon Pack pelo repositório oficial: https://github.com/SicMundus86/ProjectivyIconPack
2. No CleanYourTV: Preferências → Aplicativos → Pacote de ícones → Escolher pack.
3. Escolha Projectivy Icon Pack. O padrão é aplicar só em apps sem banner. Em Aplicação você pode escolher Todos os apps compatíveis.

O pack é um aplicativo separado. Suas imagens não estão embutidas no launcher. Não é necessário instalar o launcher Projectivy. A aplicação é feita nas preferências do CleanYourTV, sem depender do botão Aplicar do dashboard do pack.

## Escolher uma imagem manualmente

Segure OK no aplicativo → Trocar ícone ou imagem → Escolher no pacote de ícones. Use a busca e as prévias para selecionar. A escolha manual vale para aquele pacote de aplicativo, mesmo quando a activity não consta no mapa automático. Escolher uma imagem do pack substitui uma imagem de arquivo anteriormente aplicada ao mesmo app.

Use Usar correspondência automática do pack para remover apenas essa escolha manual. Restaurar nome e ícone também remove a imagem de arquivo e o nome personalizado desse app, voltando às regras automáticas.

## Prioridade das imagens

1. Imagem personalizada de arquivo/copiada de outro app, quando existir.
2. Escolha manual do pack atualmente selecionado.
3. Correspondência automática do pack, conforme Só apps sem banner ou Todos os apps compatíveis.
4. Banner original; se não existir, ícone original com o nome.

Imagens de arquivo já personalizadas são preservadas ao ativar um pack globalmente. Os logos do pack são centralizados em cartões de proporção horizontal, com transparência e proporção preservadas.

## Formato e limites

O suporte lê appfilter em res/xml, res/raw ou assets, com prioridade ao XML compilado. Aceita o catálogo drawable.xml e os nomes de imagens do appfilter. O pareamento usa o componente exato, normalizando classes relativas; quando as activities de um mesmo pacote apontam todas para a mesma imagem, permite correspondência segura por pacote. Se houver imagens diferentes, não escolhe uma arbitrariamente para uma activity desconhecida.

Suporte a nomes de drawable/mipmap e imagens estáticas. Máscaras, fundos gerados pelo pack, calendários e relógios dinâmicos não são implementados. Packs sem esse formato podem não aparecer. Imagens sem correspondência/recurso válido mantêm a aparência original. As escolhas manuais são vinculadas ao identificador do pack.

A versão do pack é conferida no recarregamento da grade e mudanças no pacote invalidam o cache. Se o pack for removido, o launcher usa as imagens originais. A leitura é local, em segundo plano; nenhuma lista de aplicativos é enviada ao autor do pack.

## Verificação realizada

O APK oficial Projectivy Icon Pack 1.1.9 (dev.projectivy.blueprint.app) foi baixado e inspecionado. O mapa completo está em res/xml/appfilter, cujo caminho físico ofuscado no APK é res/hA.xml. Os assets contêm exemplos. Foram encontrados 962 registros no mapa compilado; o índice do launcher aceitou 952 componentes únicos. Correspondências reais de Netflix e Spotify passaram na verificação com esse mapa.

Esta verificação é estrutural e de lógica. A seleção e a exibição no Android ainda precisam de teste no aparelho.
