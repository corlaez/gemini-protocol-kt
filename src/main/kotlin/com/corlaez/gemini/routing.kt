package com.corlaez.gemini

import java.net.URLDecoder

/**
 * Route context - provides access to request data and parameters
 */
public class RouteContext(public val request: GeminiRequest) {
    private val _pathParams = mutableMapOf<String, String>()

    internal val pathParams: Map<String, String> get() = _pathParams

    internal val queryParams: Map<String, String> by lazy {
        parseQueryParams(request.url.query)
    }

    public fun pathParam(name: String): String? = _pathParams[name]
    public fun pathParamOrThrow(name: String): String =
        _pathParams[name] ?: throw IllegalStateException("Path parameter '$name' not found")

    public fun queryParam(name: String): String? = queryParams[name]
    public fun queryParamOrThrow(name: String): String =
        queryParams[name] ?: throw IllegalStateException("Query parameter '$name' not found")

    internal val path: String get() = request.url.path.ifEmpty { "/" }

    internal val remoteAddress: String get() = request.remoteAddress

    // Internal: set path parameters (used by router)
    internal fun setPathParams(params: Map<String, String>) {
        _pathParams.clear()
        _pathParams.putAll(params)
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()

        return query.split('&')
            .mapNotNull { param ->
                val parts = param.split('=', limit = 2)
                if (parts.size == 2) {
                    val key = URLDecoder.decode(parts[0], "UTF-8")
                    val value = URLDecoder.decode(parts[1], "UTF-8")
                    key to value
                } else {
                    null
                }
            }
            .toMap()
    }
}

/**
 * Route handler with context
 */
public typealias ContextRouteHandler = suspend RouteContext.() -> GeminiResponse

/**
 * Route definition with pattern matching
 */
private data class Route(
    val pattern: RoutePattern,
    val handler: ContextRouteHandler
)

/**
 * Parsed route pattern
 */
private class RoutePattern(pattern: String) {
    private val segments: List<Segment>

    init {
        segments = pattern.split('/')
            .filter { it.isNotEmpty() }
            .map { segment ->
                when {
                    segment.startsWith('{') && segment.endsWith('}') -> {
                        val paramName = segment.substring(1, segment.length - 1)
                        Segment.Parameter(paramName)
                    }
                    segment == "*" -> Segment.Wildcard
                    segment == "**" -> Segment.WildcardRecursive
                    else -> Segment.Literal(segment)
                }
            }
    }

    /**
     * Check if path matches this pattern and extract parameters
     * Returns null if no match, or map of parameters if matched
     */
    fun match(path: String): Map<String, String>? {
        val pathSegments = path.split('/')
            .filter { it.isNotEmpty() }

        return matchSegments(segments, pathSegments, 0, 0, mutableMapOf())
    }

    private fun matchSegments(
        pattern: List<Segment>,
        path: List<String>,
        patternIdx: Int,
        pathIdx: Int,
        params: MutableMap<String, String>
    ): Map<String, String>? {
        // Both exhausted - success
        if (patternIdx >= pattern.size && pathIdx >= path.size) {
            return params
        }

        // Pattern exhausted but path remains - fail
        if (patternIdx >= pattern.size) {
            return null
        }

        // Path exhausted but pattern remains - fail (unless it's optional)
        if (pathIdx >= path.size) {
            return null
        }

        return when (val segment = pattern[patternIdx]) {
            is Segment.Literal -> {
                if (segment.value == path[pathIdx]) {
                    matchSegments(pattern, path, patternIdx + 1, pathIdx + 1, params)
                } else {
                    null
                }
            }

            is Segment.Parameter -> {
                params[segment.name] = path[pathIdx]
                matchSegments(pattern, path, patternIdx + 1, pathIdx + 1, params)
            }

            is Segment.Wildcard -> {
                // Match exactly one segment
                matchSegments(pattern, path, patternIdx + 1, pathIdx + 1, params)
            }

            is Segment.WildcardRecursive -> {
                // Match zero or more segments (greedy)
                // Try matching rest of pattern with remaining path
                var result: Map<String, String>? = null
                for (skip in 0..(path.size - pathIdx)) {
                    result = matchSegments(pattern, path, patternIdx + 1, pathIdx + skip, params)
                    if (result != null) break
                }
                result
            }
        }
    }

    private sealed class Segment {
        data class Literal(val value: String) : Segment()
        data class Parameter(val name: String) : Segment()
        object Wildcard : Segment()
        object WildcardRecursive : Segment()
    }
}

/**
 * Main routing configuration
 */
public class GeminiRouting {
    private val routes = mutableListOf<Route>()
    private var notFoundHandler: ContextRouteHandler = {
        GeminiResponse.NotFound("Page not found: $path")
    }

    /**
     * Register a GET route with pattern
     * Supports:
     * - Exact paths: "/about"
     * - Path parameters: "/users/{id}"
     * - Wildcards: "/static/ *" (single segment)
     * - Recursive wildcards: "/files/ **" (multiple segments)
     */
    public fun get(pattern: String, handler: ContextRouteHandler) {
        routes.add(Route(RoutePattern(pattern), handler))
    }

    /**
     * Configure custom 404 handler
     */
    public fun notFound(handler: ContextRouteHandler) {
        notFoundHandler = handler
    }

    /**
     * Internal: Find and execute the appropriate handler
     */
    internal suspend fun handle(request: GeminiRequest): GeminiResponse {
        val path = request.url.path.ifEmpty { "/" }
        val context = RouteContext(request)

        // Try to match routes in order
        for (route in routes) {
            val params = route.pattern.match(path)
            if (params != null) {
                context.setPathParams(params)
                return route.handler(context)
            }
        }

        // No route matched - call 404 handler
        return notFoundHandler(context)
    }
}

/**
 * Extension for GeminiServer to start with routing
 */
public fun GeminiServer.startWithRoutes(configureRouting: GeminiRouting.() -> Unit) {
    val geminiRouting = GeminiRouting()
    configureRouting(geminiRouting)
    start { request -> geminiRouting.handle(request) }
}
