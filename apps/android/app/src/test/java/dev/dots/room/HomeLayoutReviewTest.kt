package dev.dots.room

import org.junit.Test
import org.junit.Assert.*
import java.io.File

class HomeLayoutReviewTest {
 @Test fun homePropsStayProportionalAndBookcaseOpensNotesInThreeViewports() {
  val base=protocolJson.decodeFromString<Manifest>(File("../../../.cache/android-assets/bami/day/home.json").readText())
  assertFalse("separate home memo removed",base.furniture.any{it.id=="memo"})
  for((w,h) in listOf(960f to 1200f,1200f to 960f,1200f to 1920f)) {
   val m=SceneLayout.fit(base,w,h).manifest
   assertEquals(w/h,m.logicalSize[0].toFloat()/m.logicalSize[1],.002f)
   m.furniture.filter{it.render}.forEach{p->
    val original=base.furniture.first{it.id==p.id}
    assertEquals("${p.id} width",original.rect[2],p.rect[2],0f)
    assertEquals("${p.id} height",original.rect[3],p.rect[3],0f)
    assertTrue("${p.id} bounds",p.rect[0]>=0&&p.rect[1]>=0&&p.rect[0]+p.rect[2]<=m.logicalSize[0]&&p.rect[1]+p.rect[3]<=m.logicalSize[1])
   }
   for(id in listOf("clock","calendar","window"))assertEquals(base.furniture.first{it.id==id}.rect[1],m.furniture.first{it.id==id}.rect[1],0f)
   val shelf=m.furniture.first{it.id=="bookcase"}.rect
   assertEquals(RoomWidget.MEMO,roomWidgetAt(m,shelf[0]+shelf[2]/2,shelf[1]+shelf[3]/2))
   val screen=m.furniture.first{it.id=="desk"}.screen!!
   assertEquals(RoomWidget.MONITOR,roomWidgetAt(m,screen[0]+screen[2]/2,screen[1]+screen[3]/2))
   assertNull(roomWidgetAt(m,m.speechAnchor[0],m.speechAnchor[1]))
  }
 }
}
