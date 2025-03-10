# RewardCommands

all -> plugin will give all the players if they hit the mob

1 -> plugin will give reward for most damager

2 -> plugin will give reward for second most damager

3 -> plugin will give reward for third damager

:

## Chance

If you add percentage (like %50.0) finish of the command, Plugin will execute command by chance

Example:

<pre><code><strong>give %player% gold_ingot 1 25.0%
</strong></code></pre>

The plugin execute the "give %player% gold\_ingot 1" command by %25.0 chance



## Permission

If you add a text between 2 $symbols, this reward will need permission.&#x20;

Example:

```
give %player% gold_ingot 1 $req_perm$
```

The plugin execute the "give %player% gold\_ingot 1" if the player has the "req\_perm" permission

Video: [https://youtu.be/BimyxgcilcM](https://youtu.be/BimyxgcilcM)



## Using Mathematical Expressions

RewardSystem now supports mathematical expressions in commands. This feature allows you to use placeholder values and constants in mathematical operations.

### Usage Format

To use mathematical expressions in commands, use the `{math:expression}` format. For example:

```
give %player% diamond {math:2*%island.level%+5}
```

This command calculates 2 times the player's island level plus 5, and gives that amount of diamonds to the player.

### Supported Operations

* Addition: `+`
* Subtraction: `-`
* Multiplication: `*`
* Division: `/`
* Modulo (remainder): `%`
* Parentheses usage: `(expression)`

### Not Supported

The following operations are no longer supported due to stability improvements:
* Functions like `min()`, `max()`, `pow()`, and `round()`
* Exponentiation: `**` or `^`

Instead, use basic arithmetic expressions for similar functionality.

### Using Placeholders

Values from PlaceholderAPI are automatically converted to numbers. If a placeholder doesn't return a numeric value, it will be treated as 0. Example:

```
give %player% diamond {math:3*%server.online%}
```

### Error Handling

If an expression cannot be evaluated (due to invalid syntax, placeholder issues, etc.), the system will log a warning and:
* For chance calculations: default to 50.0% chance
* For item amounts: default to 1

This ensures that commands still execute even when mathematical expressions have issues.

### Examples

1.  Give diamonds based on island level:

    ```
    give %player% diamond {math:2*%island.level%+5}
    ```
2.  Reward based on zombie kills:

    ```
    eco give %player% {math:5*%statistic.kill_entity.zombie%}
    ```
3.  Using decimal multipliers:

    ```
    eco give %player% {math:0.2*%island.worth%}
    ```
4.  Dynamic bonus based on player count:

    ```
    give %player% emerald {math:%server.online%/2+1}
    ```

### Mathematical Expression With Chance

Just add % after expression. Example:

```
give %player% dragon_egg 1 {math:(%damage%/1000)}%
```

### Safe Alternatives for min/max Functions

Instead of using `min()` and `max()` functions, consider these approaches:

1. For minimum value caps:
   ```
   # Instead of: {math:min(90, %player_level%)}
   # Use fixed amounts with appropriate chances
   give %player% diamond 1 {math:(%player_level% / 2)}%
   ```

2. For maximum value caps:
   ```
   # Instead of: {math:max(5, %player_level%)}
   # Ensure your expression naturally reaches your desired minimum
   give %player% emerald {math:5 + (%player_level% / 10)}
   ```

### Important Notes

* If a mathematical expression is invalid, the plugin will use safe default values
* Make sure PlaceholderAPI is installed for placeholder functionality
* Keep expressions simple for better performance and reliability
* Test your expressions thoroughly to ensure they produce expected results
