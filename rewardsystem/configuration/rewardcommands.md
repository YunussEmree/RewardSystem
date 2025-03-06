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
* Exponentiation: `**` or `^`
* Parentheses usage: `(expression)`

### Using Placeholders

Values from PlaceholderAPI are automatically converted to numbers. If a placeholder doesn't return a numeric value, it will be treated as 0. Example:

```
give %player% diamond {math:3*%server.online%}
```

### Rounding Modes

There are four different rounding modes for decimal results:

1. `floor`: Rounds down (2.8 → 2)
2. `ceil`: Rounds up (2.1 → 3)
3. `round`: Rounds to the nearest (2.4 → 2, 2.5 → 3)
4. `none`: No rounding (2.33333 → 2.33333)

You can set the rounding mode in the config.yml file as follows:

```
Math:
  rounding_mode: "floor" # Options: "floor", "ceil", "round", "none"
```

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

### Important Notes

* If a mathematical expression is invalid (such as division by zero), the result will be 0
* Make sure PlaceholderAPI is installed
* Avoid overly complex expressions
* Be aware of potential overflow issues when working with large numbers
