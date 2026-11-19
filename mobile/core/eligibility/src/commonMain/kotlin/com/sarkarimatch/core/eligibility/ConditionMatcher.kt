package com.sarkarimatch.core.eligibility

import com.sarkarimatch.core.model.Category
import com.sarkarimatch.core.model.GovtEmployee

/**
 * Evaluates a schema `Condition` against the profile. A field the profile cannot answer (null) does NOT match: a
 * relaxation or fee row is only applied when we know it applies (never guess).
 */
internal object ConditionMatcher {
    fun matches(c: Condition?, p: EligibilityProfile, category: Category = p.effectiveCategory): Boolean {
        if (c == null) return true
        c.category?.let { if (category !in it) return false }
        c.gender?.let { if (p.gender == null || p.gender !in it) return false }
        c.domicileStates?.let { if (p.domicile == null || p.domicile !in it) return false }
        c.notDomicileStates?.let { if (p.domicile == null || p.domicile in it) return false }
        c.pwd?.let { if (p.pwd != it) return false }
        c.pwdTypes?.let { if (p.pwdType == null || p.pwdType !in it) return false }
        c.esm?.let { if (p.esm != it) return false }
        c.govtEmployee?.let { wanted ->
            val g = p.govtEmployee ?: return false
            val ok = when (wanted) {
                "any" -> g != GovtEmployee.NONE
                "central" -> g == GovtEmployee.CENTRAL
                "state" -> g == GovtEmployee.STATE
                else -> false
            }
            if (!ok) return false
        }
        c.maritalStatus?.let { list ->
            val m = p.marital ?: return false
            if (list.none { it.equals(m.name, ignoreCase = true) }) return false
        }
        c.hasAttribute?.let { attrs -> if (attrs.none { it in p.extras }) return false }
        c.any?.let { alternatives -> if (alternatives.none { matches(it, p, category) }) return false }
        return true
    }
}
