#version 450 core

vec2 limit_interval(vec2 interval, float limit)
{
  float end = min(interval.x + interval.y, limit);
  return vec2(interval.x, end - interval.x);
}
