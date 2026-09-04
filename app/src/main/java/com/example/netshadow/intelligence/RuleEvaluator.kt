package com.example.netshadow.intelligence

import com.example.netshadow.data.entity.AppBaselineEntity
import com.example.netshadow.data.entity.ConnectionEventEntity
import com.example.netshadow.data.repository.TrafficStats
import com.example.netshadow.intelligence.rules.*

class RuleEvaluator {
    private val rules = listOf(
        ByteSpikeRule(),
        UnusualHourRule(),
        NewDomainRule(),
        NewIpRule(),
        NewCountryRule()
    )

    fun evaluateAll(
        event: ConnectionEventEntity,
        baseline: AppBaselineEntity?,
        stats: TrafficStats?
    ): List<RuleResult> {
        return rules.mapNotNull { it.evaluate(event, baseline, stats) }
    }
}
