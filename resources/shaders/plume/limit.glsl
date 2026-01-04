#version 450 core

#define MIN_DIVISOR 1.0e-3

uniform float <%= min-limit %>;

float <%= method-name %>(float pressure)
{
  return <%= min-limit %> / max(sqrt(pressure), MIN_DIVISOR);
}
