package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.crypto.PasswordStrength
import com.example.data.crypto.StrengthLevel
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberObsidian
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.CyberRed
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun GeneratorScreen(
    generatedPassword: String,
    strength: PasswordStrength,
    length: Int,
    includeUpper: Boolean,
    includeLower: Boolean,
    includeNumbers: Boolean,
    includeSymbols: Boolean,
    excludeLookalike: Boolean,
    onLengthChange: (Int) -> Unit,
    onToggleUpper: () -> Unit,
    onToggleLower: () -> Unit,
    onToggleNumbers: () -> Unit,
    onToggleSymbols: () -> Unit,
    onToggleExcludeLookalike: () -> Unit,
    onRegenerate: () -> Unit,
    onCopyPassword: (String) -> Unit
) {
    var rotationAngle by remember { mutableStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = rotationAngle,
        animationSpec = tween(durationMillis = 300)
    )

    val strengthColor = when (strength.level) {
        StrengthLevel.VERY_WEAK -> CyberRed
        StrengthLevel.WEAK -> CyberAmber
        StrengthLevel.FAIR -> Color(0xFFFBBF24)
        StrengthLevel.STRONG -> CyberCyan
        StrengthLevel.VERY_STRONG -> CyberEmerald
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Password Output Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(strengthColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strength.level.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = strengthColor
                        )
                    }

                    Text(
                        text = "${strength.entropyBits} bits entropy",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Generated Password in Cybersecurity Monospace Display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberSurfaceElevated)
                        .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = generatedPassword,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = CyberCyan,
                        fontSize = if (length > 28) 15.sp else if (length > 18) 18.sp else 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { strength.score / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                    color = strengthColor,
                    trackColor = CyberBorder
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons: Copy & Regenerate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onCopyPassword(generatedPassword) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_generated_password_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = CyberObsidian
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Password", fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = {
                            rotationAngle += 360f
                            onRegenerate()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberSurfaceElevated)
                            .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
                            .testTag("regenerate_password_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate",
                            tint = CyberCyan,
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(animatedRotation)
                        )
                    }
                }
            }
        }

        // Quick Presets
        Text(
            text = "Security Presets",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetChip(
                modifier = Modifier.weight(1f),
                label = "Standard",
                sub = "16 Chars",
                selected = length == 16,
                onClick = { onLengthChange(16) }
            )
            PresetChip(
                modifier = Modifier.weight(1f),
                label = "High Shield",
                sub = "24 Chars",
                selected = length == 24,
                onClick = { onLengthChange(24) }
            )
            PresetChip(
                modifier = Modifier.weight(1f),
                label = "Paranoid",
                sub = "32 Chars",
                selected = length == 32,
                onClick = { onLengthChange(32) }
            )
        }

        // Length Slider Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Password Length",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "$length characters",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = CyberCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = length.toFloat(),
                    onValueChange = { onLengthChange(it.toInt()) },
                    valueRange = 6f..48f,
                    steps = 41,
                    colors = SliderDefaults.colors(
                        thumbColor = CyberCyan,
                        activeTrackColor = CyberCyan,
                        inactiveTrackColor = CyberBorder
                    ),
                    modifier = Modifier.testTag("password_length_slider")
                )
            }
        }

        // Character Sets Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                GeneratorOptionRow(
                    title = "Uppercase Letters",
                    desc = "A, B, C... Z",
                    checked = includeUpper,
                    onCheckedChange = { onToggleUpper() }
                )
                GeneratorOptionRow(
                    title = "Lowercase Letters",
                    desc = "a, b, c... z",
                    checked = includeLower,
                    onCheckedChange = { onToggleLower() }
                )
                GeneratorOptionRow(
                    title = "Numbers",
                    desc = "0, 1, 2... 9",
                    checked = includeNumbers,
                    onCheckedChange = { onToggleNumbers() }
                )
                GeneratorOptionRow(
                    title = "Symbols & Punctuation",
                    desc = "!@#$%^&*()-_=+...",
                    checked = includeSymbols,
                    onCheckedChange = { onToggleSymbols() }
                )
                GeneratorOptionRow(
                    title = "Exclude Lookalikes",
                    desc = "Avoid confusing chars: 0, O, 1, l, I",
                    checked = excludeLookalike,
                    onCheckedChange = { onToggleExcludeLookalike() },
                    isLast = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun PresetChip(
    modifier: Modifier = Modifier,
    label: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) CyberCyan.copy(alpha = 0.15f) else CyberSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) CyberCyan else CyberBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (selected) CyberCyan else TextPrimary
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun GeneratorOptionRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberObsidian,
                checkedTrackColor = CyberCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = CyberSurfaceElevated
            )
        )
    }
}
