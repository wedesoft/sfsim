#version 450 core


vec2 subtract_interval(vec2 a, vec2 b)
{
  if (a.x < b.x)
    return vec2(a.x, min(a.x + a.y, b.x) - a.x);
  else {
    float result_x = max(a.x, b.x + b.y);
    return vec2(result_x, a.x + a.y - result_x);
  };
}
