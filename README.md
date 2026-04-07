# APKM F-Droid AddOn

Esse é um exemplo funcional de AddOn para o APKM, ele faz a ponte entre o APKM e o site do fdroid.
O código-fonte é de uso livre e pode ser utilizado para criar outros AddOns por terceiros bastando manter a lógica original.

## Existem dois tipos de AddOns suportados pelo APKM:
AddOns estáticos que apenas fornecem um JSON de repositório.
AddOns dinâmicos que operam em paralelo com o APKM fornecendo dados em tempo real, 
o que também significa que se não forem feitos corretamente podem travar a execução do APKM

## Como esse AddOn funciona:
Esse AddOn de exemplo faz a leitura do html do site do F-Droid e obtem as informações necessárias
