package dev.dots.room

enum class LayerKind { FURNITURE, AMBIENT, COWORKER, ACTOR }
data class SceneLayer(val kind:LayerKind,val index:Int,val depth:Float,val part:String="all")

/** Drawing and alpha hit testing consume this exact ordered list. */
object SceneLayers {
 fun ordered(m:Manifest,foot:Point,held:Boolean,seat:Boolean,bed:Boolean,facing:String,phase:String?,progress:Float,frontCoworkers:Set<Int> = emptySet()):List<SceneLayer> {
  val layers=mutableListOf<SceneLayer>()
  m.ambient.forEachIndexed{i,a->layers.add(SceneLayer(LayerKind.AMBIENT,i,a.depth?:-1000f))}
  m.furniture.forEachIndexed{i,p->
   when {
    p.id.startsWith("colleague-chair-")->{layers.add(SceneLayer(LayerKind.FURNITURE,i,p.depth-3,"back"));layers.add(SceneLayer(LayerKind.FURNITURE,i,p.depth,"front"))}
    bed&&p.id=="bed"->{layers.add(SceneLayer(LayerKind.FURNITURE,i,foot.y-2,"back"));layers.add(SceneLayer(LayerKind.FURNITURE,i,if(phase=="exit"&&progress>=.45f)foot.y-1 else foot.y+2,"front"))}
    seat&&p.id=="chair"->{
     val front=facing=="N"&&!(phase=="enter"&&progress<.25f||phase=="exit"&&progress>.75f)
     layers.add(SceneLayer(LayerKind.FURNITURE,i,foot.y+if(front)2f else -2f))
    }
    else->layers.add(SceneLayer(LayerKind.FURNITURE,i,p.depth))
   }
  }
  m.coworkers.forEachIndexed{i,n->
   val desk=m.furniture.firstOrNull{it.id=="colleague-desk-${n.slot}"}
   // The rear seated master rests on its own chair; only the low backrest is in front.
   layers.add(SceneLayer(LayerKind.COWORKER,i,desk?.depth?.plus(if(n.slot in frontCoworkers)3f else 1f)?:n.position[1]))
  }
  layers.add(SceneLayer(LayerKind.ACTOR,0,if(held)20000f else foot.y))
  return layers.sortedBy{it.depth}
 }
 fun occluders(order:List<SceneLayer>)=order.drop(order.indexOfFirst{it.kind==LayerKind.ACTOR}+1)
}
