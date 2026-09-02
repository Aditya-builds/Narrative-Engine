from langgraph.graph import END, START, StateGraph
from langgraph.prebuilt import ToolNode

from graph.nodes import call_llm, load_context, route_llm, select_context, update_memory
from graph.state import ConversationState
from tools import TOOLS


def compile_graph():
    # One graph is the orchestrator:
    # load_context     Character/Persona runtime objects
    # select_context   deterministic layer pick for this turn
    # llm              speak / decide that an event happened (budgeted)
    # tools            rule-based Quarkus updates (no LLM)
    # update_memory    spoken reply, memories, state-change notes (no LLM)
    graph = StateGraph(ConversationState)
    graph.add_node("load_context", load_context)
    graph.add_node("select_context", select_context)
    graph.add_node("llm", call_llm)
    graph.add_node("tools", ToolNode(TOOLS))
    graph.add_node("update_memory", update_memory)
    graph.add_edge(START, "load_context")
    graph.add_edge("load_context", "select_context")
    graph.add_edge("select_context", "llm")
    graph.add_conditional_edges("llm", route_llm, {"tools": "tools", "update_memory": "update_memory"})
    graph.add_edge("tools", "llm")
    graph.add_edge("update_memory", END)
    return graph.compile()
