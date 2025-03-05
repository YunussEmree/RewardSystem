# Using Mathematical Expressions

RewardSystem now supports mathematical expressions in commands. This feature allows you to use placeholder values and constants in mathematical operations.

## Usage Format

To use mathematical expressions in commands, use the `{math:expression}` format. For example:

```
/give %player% diamond {math:2*%island.level%+5}
```

This command calculates 2 times the player's island level plus 5, and gives that amount of diamonds to the player.

## Supported Operations

- Addition: `+`
- Subtraction: `-`
- Multiplication: `*`
- Division: `/`
- Modulo (remainder): `%`
- Exponentiation: `**` or `^`
- Parentheses usage: `(expression)`

## Using Placeholders

Values from PlaceholderAPI are automatically converted to numbers. If a placeholder doesn't return a numeric value, it will be treated as 0.

Example:
```
/give %player% diamond {math:3*%server.online%}
```

## Dynamic Chance Percentages

You can now use mathematical expressions to calculate chance percentages dynamically. Use the following format:

```
/give %player% diamond 5 {math:10+%damage%*0.5}%
```

This means the chance of receiving diamonds increases with damage dealt. A player who dealt 100 damage would have a 60% chance (10 + 100*0.5 = 60%).

Other examples:
```
/give %player% emerald 3 {math:%island.level%*2}%  # 2% per island level
/eco give %player% 1000 {math:5+(%damage%/100)}%   # Base 5% + 1% per 100 damage
```

## Rounding Modes

There are four different rounding modes for decimal results:

1. `floor`: Rounds down (2.8 → 2)
2. `ceil`: Rounds up (2.1 → 3)
3. `round`: Rounds to the nearest (2.4 → 2, 2.5 → 3)
4. `none`: No rounding (2.33333 → 2.33333)

You can set the rounding mode in the config.yml file as follows:

```yaml
Math:
  rounding_mode: "floor" # Options: "floor", "ceil", "round", "none"
```

## Examples

1. Give diamonds based on island level:
   ```
   /give %player% diamond {math:2*%island.level%+5}
   ```

2. Reward based on zombie kills:
   ```
   /eco give %player% {math:5*%statistic.kill_entity.zombie%}
   ```

3. Using decimal multipliers:
   ```
   /eco give %player% {math:0.2*%island.worth%}
   ```

4. Dynamic bonus based on player count:
   ```
   /give %player% emerald {math:%server.online%/2+1}
   ```

5. Dynamic chance based on damage:
   ```
   /give %player% netherite_ingot 1 {math:%damage%*0.1}%
   ```

## Important Notes

- If a mathematical expression is invalid (such as division by zero), the result will be 0
- Make sure PlaceholderAPI is installed
- Avoid overly complex expressions
- Be aware of potential overflow issues when working with large numbers
- For dynamic chance percentages, the calculated value is treated as a percentage (0-100 range) 