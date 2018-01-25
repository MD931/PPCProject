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
          println(nodesAlive)
          if(nodesAlive(nodeId) != -1){
            //Mettre à jour sa date
            datesForChecking = datesForChecking.updated(nodeId, new Date)
          }else{
            nodesAlive = nodesAlive.updated(nodeId, 1)
            datesForChecking = datesForChecking.updated(nodeId, new Date)
            father ! Message("New node created "+nodeId)
          }
          /*if(!nodesAlive.contains(nodeId)){
            father ! Message("New node created "+nodeId)
            nodesAlive = nodeId :: nodesAlive
            datesForChecking = new Date :: datesForChecking
          }else{
            datesForChecking = datesForChecking.updated(nodesAlive.indexOf(nodeId), new Date)
          }*/
        }

        case IsAliveLeader (nodeId) =>{

          if(nodeId != leader){
            leader = nodeId
            println("The new leader is "+leader)
          }
          if(nodesAlive(nodeId) != -1){
            //Mettre à jour sa date
            datesForChecking = datesForChecking.updated(nodeId, new Date)
          }else{
            nodesAlive = nodesAlive.updated(nodeId, 1)
            datesForChecking = datesForChecking.updated(nodeId, new Date)
            father ! Message("The leader node is "+nodeId)
          }
          /*if(!nodesAlive.contains(nodeId)){
            father ! Message("The leader node is "+nodeId)
            nodesAlive = nodeId :: nodesAlive
            datesForChecking = new Date :: datesForChecking
          }else{
            datesForChecking = datesForChecking.updated(nodesAlive.indexOf(nodeId), new Date)
          }*/
        }

        // A chaque fois qu'on recoit un CheckerTick : on verifie qui est mort ou pas
        // Objectif : lancer l'election si le leader est mort
        case CheckerTick => {
          father ! Message("CheckerTick")
          lastDate = new Date
          for(i <- 0 until datesForChecking.length){
            if(id != i){
              if((lastDate.getTime - datesForChecking(i).getTime)>3000){

                if(i == leader && nodesAlive(i) != -1){
                  nodesAlive = nodesAlive.updated(i, -1)
                  electionActor ! StartWithNodeList(nodesAlive)
                }
                nodesAlive = nodesAlive.updated(i, -1)
              }
            }
          }
          /*datesForChecking.foreach(date => {
            if((lastDate.getTime - date.getTime)>time){
              val i = datesForChecking.indexOf(date)
              nodesAlive = nodesAlive diff List(nodesAlive(i))
              datesForChecking = datesForChecking diff List(date)
            }
          })*/
          scheduler.scheduleOnce(3000 milliseconds, self , CheckerTick )
        }

    }


}
