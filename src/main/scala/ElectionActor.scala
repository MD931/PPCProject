package upmc.akka.leader

import akka.actor._

abstract class NodeStatus
case class Passive () extends NodeStatus
case class Candidate () extends NodeStatus
case class Dummy () extends NodeStatus
case class Waiting () extends NodeStatus
case class Leader () extends NodeStatus

abstract class LeaderAlgoMessage
case class Initiate () extends LeaderAlgoMessage
case class ALG (list:List[Int], nodeId:Int) extends LeaderAlgoMessage
case class AVS (list:List[Int], nodeId:Int) extends LeaderAlgoMessage
case class AVSRSP (list:List[Int], nodeId:Int) extends LeaderAlgoMessage
//Class utilisée pour réinitilisé la variable Status
case class Reinitialize () extends LeaderAlgoMessage

case class StartWithNodeList (list:List[Int])

class ElectionActor (val id:Int, val terminaux:List[Terminal]) extends Actor {

     val father = context.parent
     var nodesAlive:List[Int] = List(id)

     var candSucc:Int = -1
     var candPred:Int = -1
     var status:NodeStatus = new Passive ()

     var allNodes: List[ActorSelection] = List()

     def getNeigh(nodeId : Int) : Int = {
       val neigh = (nodeId +1)%4
       if(nodesAlive(neigh) != -1 ) return neigh
       else return getNeigh(neigh)
     }

     def getRemoteId(nodeId:Int) : ActorSelection ={
       val remote = context.actorSelection("akka.tcp://LeaderSystem" + terminaux(nodeId).id + "@" + terminaux(nodeId).ip + ":" + terminaux(nodeId).port + "/user/Node/electionActor")
       return remote
     }

     def receive = {

          // Initialisation
          case Start => {
               self ! Initiate
          }

          case StartWithNodeList (list) => {
            println("StartElection")
               if (list.isEmpty) {
                    this.nodesAlive = this.nodesAlive:::List(id)
               }
               else {
                    this.nodesAlive = list
               }

               terminaux.foreach(n => {
                         val remote = context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node/electionActor")
                         // Mise a jour de la liste des nodes
                         this.allNodes = this.allNodes:::List(remote)
               })

               // Debut de l'algorithme d'election
               self ! Initiate
          }

          //Mettre le status à passive lorsque un nouveau leader est élu
          case Reinitialize =>{
            status = new Passive()
          }

          case Initiate =>{
            //println("Initiate id="+id+" status="+status+" nodesAlive="+nodesAlive)
            status match{
            case Passive()=>{
                status = Candidate()
                getRemoteId(getNeigh(id)) ! ALG(nodesAlive, id)
              }
            case _ =>{}
            }
          }

          case ALG (list, init) =>{
            //println("ALG init="+init+" status"+status+" candPred="+candPred+" candSucc="+candSucc)
            nodesAlive = list
            status match {
              case Passive() => {
                status = Dummy()
                getRemoteId(getNeigh(id)) ! ALG(list, init)
              }
              case Candidate() =>{
                candPred = init
                if(id > init){
                  if(candSucc == -1){
                    status = Waiting()
                    getRemoteId(init) ! AVS(list, id)
                  }else{
                    getRemoteId(candSucc) ! AVSRSP(list, candPred)
                    status = Dummy()
                  }
                }else if(id == init){
                  status = Leader()
                  father ! LeaderChanged(id)
                }
              }
              case Dummy() =>{
                status = new Passive()
                self ! Initiate
              }
            }
          }

          case AVS (list, j) =>{
            //println("AVS j="+j+" status"+status+" candPred="+candPred+" candSucc="+candSucc)
            status match{
              case Candidate() =>{
                if(candPred == -1) candSucc = j
                else{
                  getRemoteId(j) ! AVSRSP(list, candPred)
                  status = Dummy()
                }
              }
              case Waiting() =>{
                candSucc = j
              }
            }
          }

          case AVSRSP (list, k) =>{
            //println("AVSRSP k="+k+" status"+status+" candPred="+candPred+" candSucc="+candSucc)
            status match{
              case Waiting() =>{
                if(id == k){
                  status = Leader()
                  father ! LeaderChanged(id)
                }
                else{
                  candPred = k;
                  if(candSucc == -1){
                    if(id > k){
                      status = Waiting()
                      getRemoteId(k) ! AVS(list, id)
                    }
                  }else{
                    status = Dummy()
                    getRemoteId(candSucc) ! AVSRSP(list, k)
                  }
                }
              }
            }
          }

     }

}
