#version 410 core

uniform sampler2D day;
uniform sampler2D night;
uniform sampler2D normals;
uniform sampler2D water;
uniform vec3 water_color;
uniform vec3 light_direction;
uniform float reflectivity;
uniform float dawn_start;
uniform float dawn_end;

in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
} fs_in;

out vec3 fragColor;

vec3 direct_light(vec3 point);
vec3 phong(vec3 ambient, vec3 light, vec3 point, vec3 normal, vec3 color, float reflectivity);
vec3 attenuation_point(vec3 point, vec3 incoming);
vec3 surface_radiance_function(vec3 point, vec3 light_direction);
float remap(float value, float original_min, float original_max, float new_min, float new_max);
vec4 cloud_overlay();

// Render planet surface as seen through the atmosphere.
void main()
{
  vec3 land_normal = texture(normals, fs_in.colorcoord).xyz;
  vec3 water_normal = normalize(fs_in.point);
  vec3 day_color = texture(day, fs_in.colorcoord).rgb;
  vec3 night_color = max(texture(night, fs_in.colorcoord).rgb - 0.3, 0.0) / 0.7;
  float wet = texture(water, fs_in.colorcoord).r;
  vec3 normal = mix(land_normal, water_normal, wet);
  vec3 light = direct_light(fs_in.point);
  vec3 ambient_light = surface_radiance_function(fs_in.point, light_direction);
  vec3 color = mix(day_color, water_color, wet);
  vec3 emissive = clamp(remap(dot(light_direction, water_normal), dawn_start, dawn_end, 1.0, 0.0), 0.0, 1.0) * night_color;
  vec3 phong = phong(ambient_light, light, fs_in.point, normal, color, wet * reflectivity);
  vec3 incoming = attenuation_point(fs_in.point, phong + emissive);
  vec4 cloud_scatter = cloud_overlay();
  fragColor = incoming * (1 - cloud_scatter.a) + cloud_scatter.rgb;
}
