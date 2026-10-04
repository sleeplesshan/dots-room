package dev.dots.room

import org.junit.Assert.*
import org.junit.Test

class OfficeLifeTest {
 @Test fun touchCyclesWithoutRepeatingForOneTapAndReturnsToWork(){
  val life=OfficeLife();assertTrue(life.tap(0,1000));assertFalse(life.tap(0,1100));assertEquals("look",life.at(0,1400,1000,false).action)
  assertTrue(life.tap(0,1500));assertEquals("wave",life.at(0,1800,1000,false).action)
  assertTrue(life.tap(0,2000));assertEquals("coffee",life.at(0,2500,1000,false).action);assertEquals("work",life.at(0,10000,1000,false).action)
  assertTrue(life.tap(0,11000));assertEquals("look",life.at(0,11500,1000,false).action);assertEquals("work",life.at(1,11500,1000,false).action)
 }
 @Test fun coffeeIsStaggeredAndConversationIsSharedInPairs(){
  for(slot in 0..3){val start=24000+slot*9000L;assertEquals("work",OfficeLife.automatic(slot,start-1,false).action);assertEquals("coffee",OfficeLife.automatic(slot,start,false).action);assertEquals("work",OfficeLife.automatic(slot,start+9000,false).action);assertEquals("work",OfficeLife.automatic(slot,start,true).action)}
  assertEquals("chat",OfficeLife.automatic(0,84000,false).action);assertEquals("chat",OfficeLife.automatic(1,84000,false).action);assertEquals("work",OfficeLife.automatic(2,84000,false).action)
  assertEquals("chat",OfficeLife.automatic(2,96000,false).action);assertEquals("chat",OfficeLife.automatic(3,96000,false).action)
 }
 @Test fun turnsAndPoseSwitchesKeepTheSameHipAcrossAllMasterFrames(){
  val n=Coworker(0,listOf(52f,208f));val poses=listOf(OfficePose(),OfficePose("look",1000,6000),OfficePose("coffee",1000,8000),OfficePose("wave",1000,6000),OfficePose("chat",1000,8000))
  for(p in poses){val rect=CoworkerPose.rect(n,p);assertEquals(204f,rect[1]+if(p.front)52f else 60f,.001f);assertEquals(52f,rect[0]+32,.001f);assertEquals(64f,rect[2],0f)}
  assertEquals(0,OfficePose("coffee",0,8000).cell());assertEquals(2,OfficePose("coffee",400,8000).cell());assertEquals(0,OfficePose("coffee",7900,8000).cell());assertEquals(2,OfficePose("coffee",0,8000).cell(true))
 }
}
