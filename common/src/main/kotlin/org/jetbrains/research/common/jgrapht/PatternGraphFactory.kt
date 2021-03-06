package org.jetbrains.research.common.jgrapht

import org.jetbrains.research.common.PatternGraph
import org.jetbrains.research.common.jgrapht.edges.PatternSpecificEdge
import org.jetbrains.research.common.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.common.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.common.pyflowgraph.models.PyFlowGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.graph.DirectedMultigraph
import org.jgrapht.nio.Attribute
import org.jgrapht.nio.dot.DOTImporter
import java.io.InputStream


/**
 * These utilities provides functionality for building `DirectedAcyclicGraph` of pattern
 * from base graph with variable nodes generalization and from original `PyFlowGraph`.
 * Actually, it imitates a factory methods for the user.
 *
 * The provided graph is something like an interlayer, because it is needed only for
 * locating isomorphic subgraphs using JGraphT library methods.
 */

fun PatternGraph(
    baseDirectedAcyclicGraph: PatternGraph,
    labelsGroupsByVertexId: Map<Int, PatternSpecificVertex.LabelsGroup>
): PatternGraph {
    val targetGraph = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
        PatternSpecificMultipleEdge::class.java
    )
    val verticesMapping = HashMap<PatternSpecificVertex, PatternSpecificVertex>()
    for (vertex in baseDirectedAcyclicGraph.vertexSet()) {
        val newVertex = vertex.copy()
        if (vertex.label?.startsWith("var") == true) {
            newVertex.dataNodeInfo = labelsGroupsByVertexId[vertex.id] ?: PatternSpecificVertex.LabelsGroup.getEmpty()
        }
        targetGraph.addVertex(newVertex)
        verticesMapping[vertex] = newVertex
    }
    for (edge in baseDirectedAcyclicGraph.edgeSet()) {
        targetGraph.addEdge(
            verticesMapping[baseDirectedAcyclicGraph.getEdgeSource(edge)],
            verticesMapping[baseDirectedAcyclicGraph.getEdgeTarget(edge)],
            edge.copy()
        )
    }
    return targetGraph
}

fun PatternGraph(pfg: PyFlowGraph): PatternGraph {
    val defaultDAG = DirectedMultigraph<PatternSpecificVertex, PatternSpecificEdge>(
        PatternSpecificEdge::class.java
    )
    var edgeGlobalId = 0
    for (node in pfg.nodes) {
        val sourceVertex = PatternSpecificVertex(node)
        defaultDAG.addVertex(sourceVertex)
        for (outEdge in node.outEdges) {
            val targetVertex = PatternSpecificVertex(outEdge.nodeTo)
            if (!defaultDAG.containsVertex(targetVertex)) {
                defaultDAG.addVertex(targetVertex)
            }
            defaultDAG.addEdge(
                sourceVertex,
                targetVertex,
                PatternSpecificEdge(
                    id = edgeGlobalId++,
                    xlabel = outEdge.label,
                    fromClosure = outEdge.fromClosure
                )
            )
        }
    }
    val targetDAG = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
        PatternSpecificMultipleEdge::class.java
    )
    defaultDAG.vertexSet().forEach { targetDAG.addVertex(it) }
    for (sourceVertex in targetDAG.vertexSet()) {
        val children = defaultDAG.outgoingEdgesOf(sourceVertex).map { defaultDAG.getEdgeTarget(it) }.toSet()
        for (targetVertex in children) {
            val multipleEdge = PatternSpecificMultipleEdge(
                id = edgeGlobalId++,
                embeddedEdgeByXlabel = HashMap()
            )
            for (outEdge in defaultDAG.getAllEdges(sourceVertex, targetVertex)) {
                multipleEdge.embeddedEdgeByXlabel[outEdge.xlabel] = outEdge
            }
            targetDAG.addEdge(sourceVertex, targetVertex, multipleEdge)
        }
    }
    return targetDAG
}

fun PatternGraph(stream: InputStream): PatternGraph {
    val importer = DOTImporter<String, DefaultEdge>()
    importer.setVertexFactory { id -> id }
    val vertexAttributes = HashMap<String, HashMap<String, Attribute>>()
    val edgeAttributes = HashMap<DefaultEdge, HashMap<String, Attribute>>()
    importer.addVertexAttributeConsumer { pair, attr ->
        vertexAttributes.getOrPut(pair.first) { HashMap() }[pair.second] = attr
    }
    importer.addEdgeAttributeConsumer { pair, attr ->
        edgeAttributes.getOrPut(pair.first) { HashMap() }[pair.second] = attr
    }
    val importedDAG = DirectedMultigraph<String, DefaultEdge>(DefaultEdge::class.java)
    importer.importGraph(importedDAG, stream)

    val targetDAG = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
        PatternSpecificMultipleEdge::class.java
    )
    for (vertexId in importedDAG.vertexSet()) {
        val vertexColor = vertexAttributes[vertexId]?.get("color")?.toString()
        targetDAG.addVertex(
            PatternSpecificVertex(
                id = vertexId.toInt(),
                label = vertexAttributes[vertexId]?.get("label")?.toString()
                    ?.substringBefore('(')
                    ?.trim(),
                originalLabel = vertexAttributes[vertexId]?.get("label")?.toString()
                    ?.substringAfter('(')
                    ?.substringBefore(')')
                    ?.trim(),
                fromPart = if (vertexColor == "red2")
                    PatternSpecificVertex.ChangeGraphPartIndicator.BEFORE
                else
                    PatternSpecificVertex.ChangeGraphPartIndicator.AFTER,
                color = vertexColor,
                shape = vertexAttributes[vertexId]?.get("shape")?.toString(),
                metadata = vertexAttributes[vertexId]?.get("metadata")?.toString() ?: "",
                kind = vertexAttributes[vertexId]?.get("kind")?.toString()
            )
        )
    }
    var edgeGlobalId = 0
    for (sourceVertexId in importedDAG.vertexSet()) {
        val children = importedDAG.outgoingEdgesOf(sourceVertexId)
            .map { importedDAG.getEdgeTarget(it) }
            .toSet()
        for (targetVertexId in children) {
            val multipleEdge = PatternSpecificMultipleEdge(
                id = edgeGlobalId++,
                embeddedEdgeByXlabel = HashMap()
            )
            for (outEdge in importedDAG.getAllEdges(sourceVertexId, targetVertexId)) {
                val edge = PatternSpecificEdge(
                    id = edgeGlobalId++,
                    xlabel = edgeAttributes[outEdge]?.get("xlabel")?.toString(),
                    fromClosure = edgeAttributes[outEdge]?.get("from_closure")?.toString()?.toBoolean(),
                    style = edgeAttributes[outEdge]?.get("style")?.toString()
                )
                multipleEdge.embeddedEdgeByXlabel[edge.xlabel] = edge
            }
            targetDAG.addEdge(
                targetDAG.vertexSet().find { it.id == sourceVertexId.toInt() },
                targetDAG.vertexSet().find { it.id == targetVertexId.toInt() },
                multipleEdge
            )
        }
    }
    return targetDAG
}

fun PatternGraph.findVertexById(id: Int): PatternSpecificVertex =
    this.vertexSet().find { it.id == id } ?: throw NoSuchElementException()