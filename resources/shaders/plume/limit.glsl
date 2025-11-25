#version 450 core

uniform float min_limit;

float limit(float pressure)
{
  return min_limit / sqrt(pressure);
}
