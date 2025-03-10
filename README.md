# RewardSystem
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![Kotlin](https://img.shields.io/badge/kotlin-100%25-blue)

# What is this plugin?
<p>RewardSystem is a versatile Minecraft plugin designed to enhance server gameplay by providing customizable rewards when players defeat mobs. The plugin monitors player damage contributions to each mob and distributes rewards based on various configurable criteria.</p>

# Features
<p><strong>Dynamic Reward System</strong>: Customize rewards based on mob types, player participation, and damage dealt</p>
<p><strong>Mathematical Expression</strong>: Create complex probability-based rewards using mathematical expressions with player variables</p>
<p><strong>Multiple Reward Tiers</strong>: Configure different reward tiers based on player participation and damage</p>
<p><strong>Special Last Hit Rewards</strong>: Provide unique rewards to players who deliver the final blow</p>
<p><strong>World & Region Filtering</strong>: Restrict rewards to specific worlds or regions using WorldGuard integration</p>
<p><strong>Custom Messages</strong>: Configure detailed reward messages with color codes and formatting</p>
<p><strong>Minimum Damage Requirements</strong>: Set minimum damage thresholds for reward eligibility</p>
<p><strong>Mob Name Filtering</strong>: Trigger different rewards based on custom mob names</p>
<p><strong>PlaceholderAPI Support</strong>: Utilize player placeholders in reward calculations and messages</p>
<p><strong>Debug Mode</strong>: Comprehensive logging options for troubleshooting</p>

# Installation
<p>Download the latest release from GitHub or your preferred marketplace</p>
<p>Place the JAR file in your server's plugins directory</p>
<p>Restart your server</p>
<p>Edit the configuration files in the plugins/RewardSystem directory to customize your reward system</p>

# Configuration
<p> The plugin uses a YAML-based configuration system that is highly customizable. Here's a basic example: <a href="https://github.com/YunussEmree/RewardSystem/blob/kotlin/app/src/main/resources/config.yml"> Example Config File</a>

<p>For detailed configuration options, please refer to the <a href="https://senyigityunusemres-organization.gitbook.io/rewardsystem/readme/updates">Configuration Documentation</a>. </p>

# Mathematical Expressions
<p>RewardSystem supports mathematical expressions for dynamic reward calculations. You can use player-specific variables and basic arithmetic operations:</p>
<p>For more information about mathematical expressions: <a href="https://github.com/YunussEmree/RewardSystem/blob/kotlin/app/src/main/resources/math_expressions.md">Mathematical Expressions Guide</a> </p>

# Permissions
<p>rewardsystem.admin.resetcooldown - Permission to use reset cooldown command</p>
<p>rewardsystem.reload - Permission to reload the plugin</p>

# Commands
<p>/rewardsystem reload - Reloads the plugin configuration</p>
<p>/rewardsystem cooldown reset <player> <mobId> - Reset cooldown for a player for a specific mob</p>

# Support
<p>For support, feature requests, or bug reports, please open an issue on GitHub.</p>

# License
<p>This project is licensed under the Apache License 2.0 - see the <a href="https://github.com/YunussEmree/RewardSystem/blob/kotlin/LICENSE">LICENSE</a> file for details.</p>

# Contributors
<a href="https://github.com/YunussEmree">Yunus Emre Şenyiğit</a>
<a href="https://github.com/LiberaTeMetuMortis">MetuMortis</a>

