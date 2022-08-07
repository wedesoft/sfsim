#version 410 core

uniform sampler2D colors;
uniform sampler2D normals;
uniform sampler2D water;
uniform float specular;
uniform float radius;
uniform float polar_radius;
uniform float max_height;
uniform float amplification;
uniform vec3 water_color;
uniform vec3 position;
uniform vec3 light_direction;

in GEO_OUT
{
  mediump vec2 colorcoord;
  mediump vec2 heightcoord;
  highp vec3 point;
} fs_in;

out lowp vec3 fragColor;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec3 ground_radiance(vec3 point, vec3 light_direction, float water, float cos_incidence, float highlight,
                     vec3 land_color, vec3 water_color);
vec3 sky_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);

vec3 stretch(vec3 v)
{
  return vec3(v.x, v.y, v.z * radius / polar_radius);
}

// Render planet surface as seen through the atmosphere.
void main()
{
  vec3 normal = texture(normals, fs_in.colorcoord).xyz;
  vec3 direction = normalize(fs_in.point - position);
  vec3 scaled_direction = normalize(stretch(direction));
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
  vec3 scaled_position = stretch(position);
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, scaled_position, scaled_direction);
  vec3 scaled_point = stretch(fs_in.point);
  vec3 scaled_light_direction = normalize(stretch(light_direction));
  vec3 incoming = ground_radiance(scaled_point, scaled_light_direction, wet, cos_incidence, highlight, land_color, water_color);
  float a = atmosphere_intersection.x;
  float b = distance(scaled_position, scaled_point);
  fragColor = amplification * sky_track(scaled_light_direction, scaled_position, scaled_direction, a, b, incoming);
}
