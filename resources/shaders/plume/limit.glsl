#version 450 core

#define MIN_DIVISOR 1.0e-3

uniform float min_limit;

float limit(float pressure)
{
  return min_limit / max(sqrt(pressure), MIN_DIVISOR);
}
