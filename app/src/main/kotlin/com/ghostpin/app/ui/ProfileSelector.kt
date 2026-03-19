package com.ghostpin.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostpin.core.model.MovementProfile

// ── Profile Selector ──────────────────────────────────────────────────────────

/** Horizontal chip row for selecting a movement profile. */
@Composable
fun ProfileSelector(
        profiles: List<MovementProfile>,
        selectedProfile: MovementProfile,
        enabled: Boolean,
        onSelect: (MovementProfile) -> Unit,
) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                    text = "Movement Profile",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF888888),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                profiles.forEach { profile ->
                    FilterChip(
                            selected = profile == selectedProfile,
                            enabled = enabled,
                            onClick = { onSelect(profile) },
                            label = { Text(profile.name, maxLines = 1, fontSize = 13.sp) },
                            colors =
                                    FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF80CBC4),
                                            selectedLabelColor = Color(0xFF121212),
                                    ),
                    )
                }
            }
        }
    }
}
