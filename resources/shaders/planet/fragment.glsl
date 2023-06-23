#version 410 core

uniform sampler2D colors;
uniform sampler2D normals;
uniform sampler2D water;
uniform float specular;
uniform float radius;
uniform float max_height;
uniform float amplification;
uniform vec3 water_color;
uniform vec3 light_direction;
uniform vec3 origin;

in GEO_OUT
{
  vec2 colorcoord;
  vec2 heightcoord;
  vec3 point;
} fs_in;

out vec3 fragColor;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec3 ground_radiance(vec3 point, vec3 light_direction, float water, float incidence_fraction, float highlight,
                     vec3 land_color, vec3 water_color);
vec3 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);

// Render planet surface as seen through the atmosphere.
void main()
{
  vec3 normal = texture(normals, fs_in.colorcoord).xyz;
  vec3 direction = normalize(fs_in.point - origin);
  vec3 land_color = texture(colors, fs_in.colorcoord).rgb;
  float wet = texture(water, fs_in.colorcoord).r;
  float cos_incidence = dot(light_direction, normal);
  float highlight;
  if (cos_incidence > 0) {
    highlight = pow(max(dot(reflect(light_direction, normal), direction), 0), specular);
  } else {
    cos_incidence = 0.0;
    highlight = 0.0;
  };
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  vec3 incoming = ground_radiance(fs_in.point, light_direction, wet, cos_incidence, highlight, land_color, water_color) * amplification;
  float a = atmosphere_intersection.x;
  float b = distance(origin, fs_in.point);
  fragColor = attenuation_track(light_direction, origin, direction, a, b, incoming);
}
