package dev.dots.room

import kotlin.math.*

/** Expand the floor plan to the viewport, keeping each sprite and prop's proportions. */
data class SceneLayout(val manifest:Manifest,val sx:Float=1f,val sy:Float=1f){
 fun project(p:Placement)=p.copy(point=Point(p.point.x*sx,p.point.y*sy)).let{q->
  manifest.furniture.find{it.id==q.furniture}?.anchor?.let{q.copy(point=Point(it[0],it[1]))}?:q
 }
 fun canonical(p:Placement)=p.copy(point=Point(p.point.x/sx,p.point.y/sy))
 companion object {
  fun fit(base:Manifest,width:Float,height:Float):SceneLayout {
   if(width<=0||height<=0)return SceneLayout(base)
   val scale=min(width/base.logicalSize[0],height/base.logicalSize[1])
   val w=(width/scale).roundToInt();val h=(height/scale).roundToInt()
   val sx=w.toFloat()/base.logicalSize[0];val sy=h.toFloat()/base.logicalSize[1]
   fun pos(p:List<Float>)=listOf(p[0]*sx,p[1]*sy)
   fun moved(r:List<Float>,dx:Float,dy:Float)=listOf(r[0]+dx,r[1]+dy)+r.drop(2)
   val offsets=base.furniture.associate{p->
    val wall=p.id in setOf("clock","calendar","window","memo")||base.sceneId.startsWith("subway")&&p.id=="desk"
    val dx=(p.rect[0]+p.rect[2]/2)*(sx-1)
    val bakedPanel=base.sceneId.startsWith("subway")&&p.id in setOf("clock","calendar")
    val dy=if(base.sceneId=="home"&&wall)0f else (if(bakedPanel)p.rect[1]+p.rect[3]/2 else if(wall)p.rect[1] else p.rect[1]+p.rect[3])*(sy-1)
    p.id to Point(dx,dy)
   }.toMutableMap()
   // A workstation and its chair form one visual/interaction group.
   if(base.furniture.any{it.id=="memo"}){if(base.sceneId=="home")offsets["memo"]=offsets.getValue("desk");else if(base.sceneId=="office")offsets["memo"]=offsets.getValue("bookcase")}
   if(!base.sceneId.startsWith("subway")&&offsets.containsKey("chair"))offsets["chair"]=offsets.getValue("desk")
   base.coworkers.forEach{n->val d=offsets["colleague-desk-${n.slot}"]?:return@forEach;offsets["colleague-chair-${n.slot}"]=d;offsets["colleague-monitor-${n.slot}"]=d}
   val props=base.furniture.map{p->val d=offsets.getValue(p.id)
    p.copy(rect=moved(p.rect,d.x,d.y),depth=p.depth+d.y,collider=p.collider?.let{moved(it,d.x,d.y)},walkClearance=p.walkClearance?.let{moved(it,d.x,d.y)},anchor=p.anchor?.let{if(base.sceneId.startsWith("subway")&&p.id=="desk")pos(it)else listOf(it[0]+d.x,it[1]+d.y)},screen=p.screen?.let{moved(it,d.x,d.y)},widgetRect=p.widgetRect?.let{moved(it,d.x,d.y)})
   }
   val ambient=base.ambient.map{a->
    val group=when{a.kind=="window"->"window";a.kind=="cat"->"cat-footprint";a.kind=="plant"->"desk";else->null}
    val d=offsets[group]?:Point((a.rect[0]+a.rect[2]/2)*(sx-1),a.rect[1]*(sy-1))
    a.copy(rect=moved(a.rect,d.x,d.y),depth=a.depth?.plus(d.y))
   }
   val weatherWindows=base.weatherWindows.map{a->
    if(base.sceneId.startsWith("subway"))a.copy(rect=listOf(a.rect[0]*sx,a.rect[1]*sy,a.rect[2]*sx,a.rect[3]*sy))
    else {val d=offsets[a.id]?:Point((a.rect[0]+a.rect[2]/2)*(sx-1),a.rect[1]*(sy-1));a.copy(rect=moved(a.rect,d.x,d.y))}
   }
   val coworkers=base.coworkers.map{n->val d=offsets["colleague-desk-${n.slot}"]?:Point(0f,0f);n.copy(position=listOf(n.position[0]+d.x,n.position[1]+d.y))}
   // Keep the sprite's edge margins constant, including anchored furniture poses.
   val bounds=base.walkBounds.let{val top=it[1]*sy;listOf(it[0],top,w-(base.logicalSize[0]-it[2]),h-(base.logicalSize[1]-it[1]-it[3])-top)}
   val nodes=base.waypoints.map{n->val owner=base.furniture.firstOrNull{it.waypoint==n.id};val d=owner?.let{offsets[it.id]}
    n.copy(point=if(d!=null&&!(base.sceneId.startsWith("subway")&&owner.id=="desk"))listOf(n.point[0]+d.x,n.point[1]+d.y)else pos(n.point))
   }
   return SceneLayout(base.copy(logicalSize=listOf(w,h),furniture=props,ambient=ambient,weatherWindows=weatherWindows,coworkers=coworkers,waypoints=nodes,spawn=pos(base.spawn),speechAnchor=pos(base.speechAnchor),walkBounds=bounds),sx,sy)
  }
 }
}
