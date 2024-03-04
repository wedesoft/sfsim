#version 410 core

float M_PI = 3.14159265358;

uniform sampler2D day;
uniform sampler2D night;
uniform sampler2D normals;
uniform sampler2D water;
uniform float specular;
uniform float radius;
uniform float max_height;
uniform vec3 water_color;
uniform vec3 light_direction;
uniform vec3 origin;
uniform float albedo;
uniform float amplification;
uniform float reflectivity;
uniform float dawn_start;
uniform float dawn_end;

in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
} fs_in;

out vec4 fragColor;

bool is_above_horizon(vec3 point, vec3 direction);
vec3 transmittance_outer(vec3 point, vec3 direction);
vec3 surface_radiance_function(vec3 point, vec3 light_direction);
float remap(float value, float original_min, float original_max, float new_min, float new_max);
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec3 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);
vec4 cloud_overlay();
float overall_shadow(vec4 point);

// Render planet surface as seen through the atmosphere.
void main()
{
  vec3 land_normal = texture(normals, fs_in.colorcoord).xyz;
  vec3 water_normal = normalize(fs_in.point);
  vec3 direction = normalize(fs_in.point - origin);
  vec3 day_color = texture(day, fs_in.colorcoord).rgb;
  vec3 night_color = max(texture(night, fs_in.colorcoord).rgb - 0.3, 0.0) / 0.7;
  float wet = texture(water, fs_in.colorcoord).r;
  vec3 normal = mix(land_normal, water_normal, wet);
  float cos_incidence = dot(light_direction, normal);
  float incidence_fraction;
  float highlight;
  if (cos_incidence > 0) {
    float shadow = overall_shadow(vec4(fs_in.point, 1));
    incidence_fraction = cos_incidence * shadow;
    highlight = pow(max(dot(reflect(light_direction, normal), direction), 0), specular) * shadow;
  } else {
    cos_incidence = 0.0;
    incidence_fraction = 0.0;
    highlight = 0.0;
  };
  float cos_normal = dot(light_direction, water_normal);
  vec3 direct_light;
  if (is_above_horizon(fs_in.point, light_direction))
    direct_light = transmittance_outer(fs_in.point, light_direction);
  else
    direct_light = vec3(0, 0, 0);
  vec3 ambient_light = surface_radiance_function(fs_in.point, light_direction);
  vec3 color = day_color * (1 - wet) + water_color * wet;
  vec3 diffuse = (albedo / M_PI) * color * (incidence_fraction * direct_light + ambient_light);
  vec3 specular = (wet * reflectivity * highlight) * direct_light;
  vec3 night_lights = clamp(remap(cos_normal, dawn_start, dawn_end, 1.0, 0.0), 0.0, 1.0) * night_color;
  vec3 incoming = (diffuse + specular) * amplification + night_lights;
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  atmosphere.y = distance(origin, fs_in.point) - atmosphere.x;
  incoming = attenuation_track(light_direction, origin, direction, atmosphere.x, atmosphere.x + atmosphere.y, incoming);
  vec4 cloud_scatter = cloud_overlay();
  fragColor = vec4(incoming, 1.0) * (1 - cloud_scatter.a) + cloud_scatter;
}
