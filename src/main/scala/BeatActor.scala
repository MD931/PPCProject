package upmc.akka.leader

import math._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor._
import akka.io._
import akka.util.Timeout

import java.net._

sealed trait BeatMessage
case class Beat (id:Int) extends BeatMessage
case class BeatLeader (id:Int) extends BeatMessage

case class BeatTick () extends Tick

case class LeaderChanged (nodeId:Int)

class BeatActor (val id:Int) extends Actor {

     val time : Int = 50
     val father = context.parent
     var leader : Int = 0 // On estime que le premier Leader est 0
     val scheduler = context.system.scheduler


    def receive = {

         // Initialisation
        case Start => {
             self ! BeatTick
             if (this.id == this.leader) {
                  father ! Message ("I am the leader")
             }

        }

        // Objectif : prevenir tous les autres nodes qu'on est en vie
        case BeatTick => {
          if (id == leader) {
            father ! BeatLeader(id)
          }
          else{
            father ! Beat(id)
          }
          scheduler.scheduleOnce(Const.HEART_BEAT_PERIOD, self , BeatTick)
        }

        case LeaderChanged (nodeId) =>  {
          leader = nodeId;
          if(id == nodeId){
            father ! BeatLeader(id)
            father ! Message("I am new leader")
          }
          else{
            father ! Beat(id)
            father ! Message("The new leader is "+nodeId)
          }

        }

    }

}
