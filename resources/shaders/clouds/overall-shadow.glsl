#version 410 core
float opacity_cascade_lookup(vec4 point);
float shadow_cascade_lookup(vec4 point);

float overall_shadow(vec4 point)
{
  return shadow_cascade_lookup(point) * opacity_cascade_lookup(point);
}
