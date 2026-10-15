package dev.fusion.automation

sealed interface LocalAction {
    data class Open(val module: String): LocalAction
    data class Workspace(val index: Int): LocalAction
}
/** Explicit user-run own-app plan. No timers, shell, network, retries or implicit permission consent. */
class ActionPlan private constructor(private val actions: List<LocalAction>) {
    fun actions(): List<LocalAction> = actions.toList()
    data class Result(val completed: Int, val total: Int, val failure: String?)
    /** Sequential dispatch; on failure prior effects remain. This is NOT a transaction or durable job. */
    fun execute(perform: (LocalAction)->Boolean): Result {
        var done=0
        for(action in actions) {
            val ok=try { perform(action) } catch (_: Exception) { return Result(done,actions.size,"Action threw; prior effects retained") }
            if(!ok) return Result(done,actions.size,"Action refused; prior effects retained")
            done++
        }
        return Result(done,actions.size,null)
    }
    companion object {
        private val modules=setOf("editor","calculator","notes","files","settings")
        fun parse(text: String): ActionPlan {
            require(text.length in 1..4096 && '\r' !in text)
            val lines=text.split('\n');require(lines.size in 1..16)
            val actions=lines.map { line ->
                val p=line.split(' ');require(p.size==2 && p.all { it.isNotEmpty() })
                when(p[0]) {
                    "open" -> { require(p[1] in modules);LocalAction.Open(p[1]) }
                    "workspace" -> { require(p[1] in setOf("1","2","3"));LocalAction.Workspace(p[1].toInt()-1) }
                    else -> throw IllegalArgumentException("Unsupported local action")
                }
            }
            return ActionPlan(actions)
        }
    }
}
