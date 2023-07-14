#version 410 core

uniform sampler2D colors;
uniform sampler2D normals;
uniform sampler2D water;
uniform float specular;
uniform float radius;
uniform float max_height;
uniform vec3 water_color;
uniform vec3 light_direction;
uniform vec3 origin;

in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
} fs_in;

out vec3 fragColor;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec3 ground_radiance(vec3 point, vec3 light_direction, float water, float incidence_fraction, float highlight,
                     vec3 land_color, vec3 water_color);
vec3 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);
vec4 cloud_overlay();
float overall_shadow(vec4 point);

// Render planet surface as seen through the atmosphere.
void main()
{
  vec3 land_normal = texture(normals, fs_in.colorcoord).xyz;
  vec3 water_normal = normalize(fs_in.point);
  vec3 direction = normalize(fs_in.point - origin);
  vec3 land_color = texture(colors, fs_in.colorcoord).rgb;
  float wet = texture(water, fs_in.colorcoord).r;
  vec3 normal = mix(land_normal, water_normal, wet);
  float cos_incidence = dot(light_direction, normal);
  float highlight;
  if (cos_incidence > 0) {
    highlight = pow(max(dot(reflect(light_direction, normal), direction), 0), specular);
  } else {
    cos_incidence = 0.0;
    highlight = 0.0;
  };
  float incidence_fraction = cos_incidence * overall_shadow(vec4(fs_in.point, 1));
  vec3 incoming = ground_radiance(fs_in.point, light_direction, wet, incidence_fraction, highlight, land_color, water_color);
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  atmosphere.y = distance(origin, fs_in.point) - atmosphere.x;
  incoming = attenuation_track(light_direction, origin, direction, atmosphere.x, atmosphere.x + atmosphere.y, incoming);
  vec4 cloud_scatter = cloud_overlay();
  fragColor = incoming * (1 - cloud_scatter.a) + cloud_scatter.rgb;
}
