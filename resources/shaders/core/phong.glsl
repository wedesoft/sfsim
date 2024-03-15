#version 410 core

float M_PI = 3.14159265358;

uniform float albedo;
uniform float specular;
uniform vec3 origin;
uniform vec3 light_direction;
uniform float amplification;

vec3 phong(vec3 ambient, vec3 light, vec3 point, vec3 normal, vec3 color, float reflectivity)
{
  float cos_incidence = dot(light_direction, normal);
  float highlight;
  if (cos_incidence > 0.0) {
    if (reflectivity > 0.0) {
      vec3 direction = normalize(point - origin);
      highlight = pow(max(dot(reflect(light_direction, normal), direction), 0), specular);
    } else
      highlight = 0.0;
  } else {
    cos_incidence = 0.0;
    highlight = 0.0;
  };
  return amplification * ((albedo / M_PI) * color * (cos_incidence * light + ambient) + reflectivity * highlight * light);
}
