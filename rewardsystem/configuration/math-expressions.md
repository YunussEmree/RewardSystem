# Mathematical Expressions and Configuration

RewardSystem allows you to use mathematical expressions in your config.yml file. These expressions enable you to create dynamic rewards based on player level, damage dealt, and other metrics.

## Basic Syntax

Mathematical expressions use the following format: `{math:expression}`

For example:
```yaml
give %player% diamond {math:2 + (%player_level% / 10)} 
```

## Chance Expressions

Chance expressions are created by adding a percentage sign (%) at the end of the command:

```yaml
give %player% diamond 1 {math:10 + (%player_level% * 2)}%
```

This gives a 20% chance (10 + 5*2 = 20%) if the player's level is 5.

## Configuration Example

```yaml
zombie:
  id: zombie_reward
  type: ZOMBIE
  name: "Rotting Zombie"
  minimumDamagePercent: 5.0
  radius: 50
  cooldown: 60
  cooldownType: MINUTES
  
  allRewards:
    - "give %player% minecraft:coal 5"
    - "give %player% minecraft:diamond 1 50.0%"  # Fixed chance value
    - "give %player% minecraft:emerald 2 {math:20 + (%player_level% / 10)}%"  # Dynamic chance
    
  rewards:
    1:  # Most damage player
      - "give %player% minecraft:diamond 3"
      - "give %player% minecraft:netherite_ingot 1 {math:10 + (%player_level% / 100)}%"
    2:  # Second most damage player
      - "give %player% minecraft:iron_ingot 5"
      - "give %player% minecraft:diamond 1 25.0%"
      
  lastHitRewards:  # Last hit player
    - "give %player% minecraft:experience_bottle 5"
    - "give %player% minecraft:diamond 3 {math:%player_level% * 5}%"
```

## Available Variables

You can use the following variables in your mathematical expressions:

- `%player_level%`: Player's level
- `%personal_damage%`: Damage dealt by the player to the mob
- `%top_damage_1%`, `%top_damage_2%`, etc.: Damage values on the leaderboard
- All other values provided by PlaceholderAPI

## Supported Operations

- Addition: `+`
- Subtraction: `-`
- Multiplication: `*`
- Division: `/`
- Modulo (remainder): `%`
- Parentheses: `(expression)`

## Example Uses

1. Reward amount based on player level:
   ```yaml
   give %player% minecraft:diamond {math:1 + (%player_level% / 10)}
   ```

2. Chance based on damage dealt:
   ```yaml
   give %player% minecraft:nether_star 1 {math:(%personal_damage% / 20) + 5}%
   ```

3. Simple calculations:
   ```yaml
   give %player% minecraft:emerald {math:(%player_level% / 5) + 2}
   ```

4. Multi-step calculations with parentheses:
   ```yaml
   give %player% minecraft:gold_ingot {math:((%player_level% * 2) + 5) / 3}
   ```

5. Using server statistics:
   ```yaml
   give %player% minecraft:ender_pearl {math:(%server.online% + 1) / 2}
   ```

## Error Handling

If an expression cannot be evaluated (syntax error, variable issue, etc.), the system:
- For chance calculations: uses a default 50% chance
- For item amounts: uses a default value of 1

This ensures that commands continue to work even when mathematical expressions have issues.

## Practical Tips

- Keep expressions simple for better performance
- Test your expressions with various player levels and damage values
- Use debug mode to verify that expressions are evaluated correctly
- Consider edge cases like new players (low levels) or very high damage values 