# SDT3
Alunos: Álisson Braga, Natã Ismael Schmitt

Modo de uso: 
- Conexão: Para o trabalho atender ao requisito de utilizar multicast para descoberta de clientes e unicast para envio de mensagens, foi empregado a utilização de containers Docker para isolação dos processos a fim de que cada um tenha seu IP individual. Está presente nos arquivos um Dockerfile que foi utilizado para subir o container e um arquivo run.sh para compilar e rodar os clientes. O comando utilizado para criar o container foi `docker build -t nome_da_imagem .`, subir o container foi `docker run --rm -v .:/SDT3 -w /SDT3 -it --network=bridge nome_da_imagem`. Ambos são comandos que devem ser executados na pasta raiz do projeto. Estes comandos foram utilizados no PowerShell na versão 11 do Windows, com docker na versão `24.0.2`.
- Troca de mensagem: Após todos clientes que vão participar do grupo se conectarem, será possível digitar qual mensagem será enviada pelo terminal, após clicar para enviar a mensagem haverá uma pergunta se a mensagem deve ser enviada para cada membro do grupo. Responder "y" envia a mensagem, "n" guarda a mensagem para envio posterior.
- Comandos: Ao invés de enviar uma mensagem, é possível digitar "/buffer" para analisar quais mensagens estão guardadas no buffer de mensagens (não confundir com o buffer de mensagens para envio posterior). Também é possível digitar "/sendAll" para enviar todas as mensagens que ficaram guardadas para envio posterior.

Conteúdo:
- Sempre que um usuário se conecta no grupo é exibido na tela o IP do novo membro e a lista de membros atualizada.
- Sempre que uma mensagem é recebida é exibido o Vector Clock em seu piggyback, em seguida é informado se a mensagem pode ser entregue ou não, de acordo com a ordem causal. Em seguida é mostrado a Matrix Clock do cliente, em verde é o seu Vector Clock.

Funcionamento:
- Recebimento: Ao receber uma mensagem, ela é guardada no buffer de mensagens do usuário, em seguida as mensagens do buffer são ordenadas de acordo com seu VC.
- Ordem Causal: Após adicionada no buffer, todas as mensagens que estão nele são verificadas se podem ser entregues de acordo com a ordem causal, se for entregue o VC é .
- Estabilização: Se uma mensagem já foi entregue, é comparado seu VC com o VC de cada usuário (guardados na Matrix Clock), se ela ja foi entregue aos outros usuários, pode ser descartada do buffer.

## Exemplo de uso:
### [ORDEM CAUSAL]
- Com 3 clientes abertos:
- Cliente 0 envia mensagem para 1 e não para 2
- Cliente 1 envia mensagem para 0 e 2
- Middleware do cliente 2 avisa que não pode entregar mensagem de 1
- Cliente 0 executa /sendAll
- Cliente 2 recebe a primeira mensagem e a entrega, e depois entrega a mensagem de 1 que estava no buffer.

### [ESTABILIZAÇÃO]
- Com 3 clientes abertos:
- Cliente 0 envia mensagem para todos
- Cliente 1 e 2 recebem, entregam e guardam no buffer
- Cliente 1 envia mensagem para todos
- Cliente 1 recebe e guarda no buffer, cleinte 2 recebe, guarda no buffer e remove a mensagem de 0 do buffer pois sabe que 1 já recebeu.


Os algoritmos estão corretamente implementados, portanto estes são apenas casos base, podendo ser testados qualquer caso que seu funcionamento estará correto.

# JavaDoc
Pode se gerar o JavaDoc com o seguinte comando: `javadoc *.java CausalMulticast/*.java`.