package upmc.akka.leader

import java.util
import java.util.Date

import akka.actor._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

abstract class Tick
case class CheckerTick () extends Tick

class CheckerActor (val id:Int, val terminaux:List[Terminal], electionActor:ActorRef) extends Actor {

     var time : Int = 200
     val father = context.parent

     var nodesAlive:List[Int] = List(-1,-1,-1,-1)
     var datesForChecking:List[Date] = List()
     var lastDate:Date = null

     var leader : Int = -1


     val scheduler = context.system.scheduler

    def receive = {

         // Initialisation
        case Start => {
             self ! CheckerTick
             nodesAlive = nodesAlive.updated(id, 1)
             for(i <- 0 until 4){
               datesForChecking = new Date :: datesForChecking
             }

        }

        // A chaque fois qu'on recoit un Beat : on met a jour la liste des nodes
        case IsAlive (nodeId) => {
          //println(nodesAlive)
          if(nodesAlive(nodeId) != -1){
            //Mettre à jour sa date
            datesForChecking = datesForChecking.updated(nodeId, new Date)
          }else{
            nodesAlive = nodesAlive.updated(nodeId, 1)
            datesForChecking = datesForChecking.updated(nodeId, new Date)
            father ! Message("New node created "+nodeId)
          }
        }

        case IsAliveLeader (nodeId) =>{

          if(nodeId != leader){
            leader = nodeId
            father ! Message("The new leader is "+leader)
            electionActor ! Reinitialize
          }
          if(nodesAlive(nodeId) != -1){
            //Mettre à jour sa date
            datesForChecking = datesForChecking.updated(nodeId, new Date)
          }else{
            nodesAlive = nodesAlive.updated(nodeId, 1)
            datesForChecking = datesForChecking.updated(nodeId, new Date)
            father ! Message("The leader node is "+nodeId)
          }
        }

        // A chaque fois qu'on recoit un CheckerTick : on verifie qui est mort ou pas
        // Objectif : lancer l'election si le leader est mort
        case CheckerTick => {
          lastDate = new Date
          for(i <- 0 until datesForChecking.length){
            if(id != i){
              if((lastDate.getTime - datesForChecking(i).getTime).millis > Const.NODE_DECLARED_DEAD){

                if(i == leader && nodesAlive(i) != -1){
                  nodesAlive = nodesAlive.updated(i, -1)
                  electionActor ! StartWithNodeList(nodesAlive)
                }
                nodesAlive = nodesAlive.updated(i, -1)
              }
            }
          }
          scheduler.scheduleOnce(Const.CHECK_PERIOD , self , CheckerTick )
        }

    }


}
