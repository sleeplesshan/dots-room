package dev.dots.room
import org.junit.Test
import org.junit.Assert.*

class BamiRoomNavigationTest {
 private fun manifest()=javaClass.getResourceAsStream("/manifest.json")!!.bufferedReader().use{protocolJson.decodeFromString<Manifest>(it.readText())}
 @Test fun productionReadRouteUsesOuterLaneAndNeverClipsDeskSides(){val m=manifest();val center=m.waypoints.first{it.id=="center"}.point;val route=Navigation.route(Point(center[0],center[1]),"read",m.waypoints,m.furniture);assertTrue(route.isNotEmpty());val lane=m.waypoints.first{it.id=="left-lane"}.point;assertTrue(Point(lane[0],lane[1]) in route);val desk=m.furniture.first{it.id=="desk"};assertNotNull(desk.walkClearance);assertTrue(Navigation.collides(Point(desk.walkClearance!![0]+1,desk.walkClearance[1]+1),m.furniture));for(i in 0 until route.lastIndex)assertTrue(Navigation.safe(route[i],route[i+1],m.furniture))}
 @Test fun productionFurnitureRoutesAndRearToFrontTransitionRemainReachable(){val m=manifest();val motion=SceneMotion(m);for(action in listOf("read","think","work","talk","sleep","talk")){repeat(400){motion.tick(action,1f/30,false,true)};assertFalse("Blocked route: $action",motion.fallback);assertEquals(action,motion.displayAction(action));if(action in listOf("think","work")){assertTrue(motion.seatedAtDesk);assertEquals("N",motion.direction);assertNotNull(SpriteAnimator.choose(m,action,"N"))};if(action=="talk"){assertFalse(motion.seatedAtDesk);assertEquals(Point(m.speechAnchor[0],m.speechAnchor[1]),motion.position);assertEquals("S",motion.direction)}};for(node in m.waypoints)assertTrue("Unreachable waypoint ${node.id}",Navigation.route(Point(m.speechAnchor[0],m.speechAnchor[1]),node.id,m.waypoints,m.furniture).isNotEmpty())}

 @Test fun quietWaypointsBedAndRequestInterruptionHaveSafeRoutes(){
  val m=manifest();val motion=SceneMotion(m)
  for((action,target)in listOf("idle" to "left-lane","read" to "read","idle" to "center","idle" to "window","sleep" to "sleep")){
   repeat(600){motion.tick(action,1f/30,false,false,target)}
   assertFalse("Blocked ambient $target",motion.fallback);assertEquals(action,motion.displayAction(action))
  }
  assertEquals(m.furniture.first{it.id=="bed"}.anchor!!.let{Point(it[0],it[1])},motion.position)
  repeat(600){motion.tick("read",1f/30,false,true)};assertFalse(motion.fallback);assertEquals(m.waypoints.first{it.id=="read"}.point.let{Point(it[0],it[1])},motion.position)
  motion.tick("work",1f/30,true);assertTrue(motion.seatedAtDesk);assertEquals("N",motion.direction)
  motion.tick("talk",1f/30,true);assertEquals(Point(m.speechAnchor[0],m.speechAnchor[1]),motion.position);assertFalse(motion.seatedAtDesk)
 }
}
