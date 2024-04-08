#version 410 core

uniform vec3 light_direction;

bool is_above_horizon(vec3 point, vec3 direction);
vec3 transmittance_point(vec3 point);
float overall_shadow(vec4 point);

vec3 environmental_shading(vec3 point)
{
  if (is_above_horizon(point, light_direction)) {
    float shadow = overall_shadow(vec4(point, 1));
    if (shadow > 0)
      return shadow * transmittance_point(point);
    else
      return vec3(0.0, 0.0, 0.0);
  } else
    return vec3(0.0, 0.0, 0.0);
}
