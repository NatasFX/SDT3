# SDT3
Alunos: Álisson Braga, Natã Ismael Schmitt

Modo de uso: 
- Conexão: [Socorro].
- Troca de mensagem: Após todos clientes que vão participar do grupo se conectarem, será possível digitar qual mensagem será enviada pelo terminal, após clicar para enviar a mensagem haverá uma pergunta se a mensagem deve ser enviada para cada membro do grupo. Responder "y" envia a mensagem, "n" guarda a mensagem para envio posterior.
- Comandos: Ao invés de enviar uma mensagem, é possível digitar "/buffer" para analisar quais mensagens estão guardadas no buffer. Também é possível digitar "/sendAll" para enviar todas as mensagens que ficaram guardadas para envio posterior.

Conteúdo:
- Sempre que um usuário se conecta no grupo é exibido na tela o ip do novo membro e a lista de membros atualizada.
- Sempre que uma mensagem é recebida é exibido o Vector Clock em seu piggyback, em seguida é informado se a mensagem pode ser entregue ou não, de acordo com a ordem causal. Em seguida é mostrado a Matrix Clock do cliente, em verde é o seu Vector Clock.

Funcionamento:
- Recebimento: Ao receber uma mensagem, ela é guardada no buffer de mensagens do usuário, em seguida as mensagens do buffer são ordenadas de acordo com seu VC.
- Ordem Causal: Após adicionada no buffer, todas as mensagens que estão nele são verificadas se podem ser entregues de acordo com a ordem causal, se for entregue o VC é incrementado.
- Estabilização: Se uma mensagem já foi entregue, é comparado seu VC com o VC de cada usuário (guardados na Matrix Clock), se ela ja foi entregue aos outros usuários, pode ser descartada do buffer.

Exemplo de uso:
[ORDEM CAUSAL]
abrir 3 clientes;
cliente 0 envia mensagem para 1 e não para 2
cliente 1 envia mensagem para 0 e 2
middleware cliente 2 avisa que não pode entregar mensagem de 1
cliente 0 executa /sendAll
cliente 2 recebe a primeira mensagem e a entrega, e depois entrega a mensagem de 1 que estava no buffer

[ESTABILIZAÇÃO]
abrir 3 clientes;
cliente 0 envia mensagem para todos
cliente 1 e 2 recebem, entregam e guardam no buffer
cliente 1 envia mensagem para todos
cliente 1 recebe e guarda no buffer, cleinte 2 recebe, guarda no buffer e remove a mensagem de 0 do buffer pois sabe que 1 já recebeu.
