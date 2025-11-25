#version 450 core

uniform float cover_multiplier;
uniform float cloud_multiplier;
uniform float cloud_threshold;

float cloud_cover(vec3 point);
float sphere_noise(vec3 point);
float cloud_profile(vec3 point);

float cloud_base(vec3 point)
{
  float cover = cloud_cover(point) * cover_multiplier;
  if (cover + cloud_multiplier > cloud_threshold) {
    float clouds = sphere_noise(point) * cloud_multiplier;
    if (cover + clouds > cloud_threshold) {
      float profile = cloud_profile(point);
      return (cover + clouds - cloud_threshold) * profile;
    } else
      return 0.0;
  } else
    return cover + cloud_multiplier - cloud_threshold;
}
