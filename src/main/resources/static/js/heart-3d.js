/**
 * Blood Buddy — Realistic 3D Anatomical Human Heart
 * Deep Crimson & Burgundy Medical Visualization matching Blood Buddy Theme
 * 
 * Features:
 * - Natural deep crimson, burgundy, and rich blood-red myocardial tissue
 * - Subtle dark-red shadows and soft highlights for anatomical depth
 * - Major blood vessels clearly distinguishable with natural red and restrained blue-purple tones
 * - Soft crimson rim light and subtle dark-red ambient glow harmonizing with near-black website theme
 * - Physiological double-beat rhythm (lub-dub) followed by diastolic resting pause (~56 bpm)
 * - Balanced sizing (380-460px desktop) with responsive scaling on mobile
 * - Subtle ECG waveform layered behind the heart with restrained crimson glow
 * - Strictly respects prefers-reduced-motion
 */
(function () {
  'use strict';

  function isWebGLSupported() {
    try {
      var canvas = document.createElement('canvas');
      return !!(window.WebGLRenderingContext && 
        (canvas.getContext('webgl') || canvas.getContext('experimental-webgl')));
    } catch (e) {
      return false;
    }
  }

  var prefersReducedMotion = window.matchMedia && 
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initHeart3D);
  } else {
    initHeart3D();
  }

  function initHeart3D() {
    var container = document.getElementById('heart3DContainer');
    var canvas = document.getElementById('heartCanvas');
    var fallback = document.getElementById('heartFallback');
    var loader = document.getElementById('heartLoader');
    var ambientGlow = document.getElementById('heartAmbientGlow');
    var ecgTrace = document.getElementById('ecgPulseTrace');

    if (!container || !canvas) {
      return;
    }

    if (!isWebGLSupported() || typeof THREE === 'undefined' || typeof THREE.GLTFLoader === 'undefined') {
      showFallback();
      return;
    }

    function showFallback() {
      if (loader) {
        loader.style.opacity = '0';
        setTimeout(function () {
          loader.style.display = 'none';
        }, 200);
      }
      if (canvas) canvas.style.display = 'none';
      if (fallback) {
        fallback.style.display = 'flex';
        void fallback.offsetWidth;
        fallback.style.opacity = '1';
      }
    }

    // 1. Scene, Camera & Renderer
    var scene = new THREE.Scene();

    var width = container.clientWidth || 440;
    var height = container.clientHeight || 470;
    var camera = new THREE.PerspectiveCamera(36, width / height, 0.1, 50);
    camera.position.set(0, 0, 4.60);

    var renderer;
    try {
      renderer = new THREE.WebGLRenderer({
        canvas: canvas,
        alpha: true,
        antialias: true,
        powerPreference: 'high-performance'
      });
    } catch (e) {
      console.warn('[Heart3D] Failed to create WebGLRenderer:', e);
      showFallback();
      return;
    }

    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    if (renderer.outputColorSpace) {
      renderer.outputColorSpace = THREE.SRGBColorSpace;
    } else if (renderer.outputEncoding) {
      renderer.outputEncoding = THREE.sRGBEncoding;
    }

    // ACES Filmic Tone Mapping with restrained exposure (prevents white wash & preserves rich blood red)
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 0.92;

    // 2. Dark Crimson & Burgundy Medical Studio Lighting Setup
    // Deep near-black burgundy ambient fill - perfectly harmonizes with near-black website background
    var ambientLight = new THREE.AmbientLight(0x2d0810, 1.3);
    scene.add(ambientLight);

    // Front Key Light (Soft warm amber-peach) - illuminates muscle texture without bleaching
    var keyLight = new THREE.DirectionalLight(0xffdfd2, 1.3);
    keyLight.position.set(1.5, 1.6, 3.6);
    scene.add(keyLight);

    // Left Lateral Fill (Deep rich wine red) - defines left ventricle in deep crimson
    var leftFillLight = new THREE.DirectionalLight(0x991828, 1.4);
    leftFillLight.position.set(-3.0, 0.4, 2.8);
    scene.add(leftFillLight);

    // Right Anterior Fill (Deep blood red) - illuminates right atrium & sulcus
    var rightFillLight = new THREE.DirectionalLight(0xb71c1c, 1.2);
    rightFillLight.position.set(3.0, 0.2, 2.6);
    scene.add(rightFillLight);

    // Inferior Apex Fill Light (Dark ruby) - keeps apex defined against dark surface
    var apexLight = new THREE.DirectionalLight(0x8e001a, 1.4);
    apexLight.position.set(0.2, -3.2, 2.2);
    scene.add(apexLight);

    // Superior Aortic Fill (Deep rose-ruby) - tones down harsh white glare on aorta
    var topVesselLight = new THREE.DirectionalLight(0xc2185b, 0.9);
    topVesselLight.position.set(0.0, 3.4, 1.8);
    scene.add(topVesselLight);

    // Right Crimson Rim Light (Restrained deep ruby rim - no pink flare)
    var rightRimLight = new THREE.DirectionalLight(0xc62828, 2.0);
    rightRimLight.position.set(3.6, 0.8, -2.5);
    scene.add(rightRimLight);

    // Left Dark Wine Rim Light
    var leftRimLight = new THREE.DirectionalLight(0x8e001a, 1.6);
    leftRimLight.position.set(-3.6, 1.0, -2.4);
    scene.add(leftRimLight);

    // Top Aorta Rim Light (Warm ruby contour)
    var topRimLight = new THREE.DirectionalLight(0x9a001a, 1.2);
    topRimLight.position.set(0.0, 3.8, -1.8);
    scene.add(topRimLight);

    // Bottom Apex Contour Light
    var bottomRimLight = new THREE.DirectionalLight(0x7b0014, 1.4);
    bottomRimLight.position.set(0.0, -3.6, -1.6);
    scene.add(bottomRimLight);

    // Restrained Deep Blue-Purple Venous Accent - distinguishes vena cava & pulmonary artery
    var venousAccentLight = new THREE.DirectionalLight(0x1e3a8a, 1.0);
    venousAccentLight.position.set(-2.8, 2.6, 1.2);
    scene.add(venousAccentLight);

    // Subsurface Myocardial Core Light - subtle internal blood pulse
    var coreLight = new THREE.PointLight(0x800512, 1.2, 5.0, 1.5);
    coreLight.position.set(0.05, -0.1, 0.5);
    scene.add(coreLight);

    // 3. Heart Pivot Group
    var heartGroup = new THREE.Group();
    scene.add(heartGroup);

    var baseScale = 1.0;
    var baseRotX = 0.08;
    var baseRotY = -0.30; // Anatomical 3/4 presentation
    var baseRotZ = -0.04;
    heartGroup.rotation.set(baseRotX, baseRotY, baseRotZ);

    var heartMaterial = null;

    // 4. Load 3D Anatomical Model
    var gltfLoader = new THREE.GLTFLoader();
    var modelUrl = '/static/models/human_heart.glb';

    gltfLoader.load(
      modelUrl,
      function (gltf) {
        var model = gltf.scene;

        var box = new THREE.Box3().setFromObject(model);
        var center = box.getCenter(new THREE.Vector3());
        var size = box.getSize(new THREE.Vector3());

        // Center geometry with optimal vertical headroom for aorta and apex
        model.position.x -= center.x;
        model.position.y -= (center.y - 0.04);
        model.position.z -= center.z;

        // Balanced size target: renders ~390-440px on desktop (within 380-460px specification)
        var maxDim = Math.max(size.x, size.y, size.z);
        var targetSize = 2.45;
        baseScale = targetSize / maxDim;
        heartGroup.scale.set(baseScale, baseScale, baseScale);

        model.traverse(function (child) {
          if (child.isMesh && child.material) {
            child.castShadow = false;
            child.receiveShadow = false;

            if (child.material.isMeshStandardMaterial || child.material.isMeshPhysicalMaterial) {
              heartMaterial = child.material;

              // Tone down green & blue channels to transform pale pink/white into authentic deep blood red & crimson
              if (heartMaterial.color) {
                heartMaterial.color.setRGB(1.15, 0.32, 0.38);
              }

              // Deep biological tissue sheen: smooth moist pericardium with diffused, soft specular highlights
              heartMaterial.roughness = 0.50;
              heartMaterial.metalness = 0.02;

              // Living biological vascular emissive warmth (dark wine red, not pink)
              heartMaterial.emissive = new THREE.Color(0x4a040d);
              heartMaterial.emissiveIntensity = 0.20;

              // Emphasize normal map depth for prominent coronary vessels and muscle striations
              if (heartMaterial.normalScale) {
                heartMaterial.normalScale.set(1.5, 1.5);
              }

              if (heartMaterial.map) {
                if (heartMaterial.map.colorSpace) {
                  heartMaterial.map.colorSpace = THREE.SRGBColorSpace;
                } else {
                  heartMaterial.map.encoding = THREE.sRGBEncoding;
                }
              }

              heartMaterial.needsUpdate = true;
            }
          }
        });

        heartGroup.add(model);

        // Pre-render initial frame so canvas is fully painted before transition
        renderer.render(scene, camera);

        // Reveal 3D view smoothly
        if (loader) {
          loader.style.opacity = '0';
          setTimeout(function () {
            loader.style.display = 'none';
          }, 300);
        }
        if (fallback) {
          fallback.style.display = 'none';
          fallback.style.opacity = '0';
        }
        canvas.style.opacity = '1';

        if (prefersReducedMotion) {
          if (heartMaterial) {
            heartMaterial.emissiveIntensity = 0.26;
          }
          if (coreLight) {
            coreLight.intensity = 1.8;
          }
          renderer.render(scene, camera);
          return;
        }

        animate();
      },
      undefined,
      function (error) {
        console.warn('[Heart3D] Error loading 3D heart model, falling back to SVG illustration:', error);
        showFallback();
      }
    );

    // 5. Interactive Mouse Parallax
    var mouseX = 0;
    var mouseY = 0;
    var targetMouseX = 0;
    var targetMouseY = 0;

    var heroSection = document.getElementById('hero') || document.body;
    heroSection.addEventListener('mousemove', function (e) {
      if (prefersReducedMotion) return;
      var rect = heroSection.getBoundingClientRect();
      var x = (e.clientX - rect.left) / rect.width - 0.5;
      var y = (e.clientY - rect.top) / rect.height - 0.5;
      targetMouseX = x * 2;
      targetMouseY = y * 2;
    });

    heroSection.addEventListener('mouseleave', function () {
      targetMouseX = 0;
      targetMouseY = 0;
    });

    // 6. Realistic Continuous Cardiac Cycle Animation Loop
    var startTime = performance.now();
    var cycleDuration = 1.08; // ~56 bpm natural resting cardiac cycle

    function animate() {
      requestAnimationFrame(animate);

      var elapsed = (performance.now() - startTime) / 1000;
      var phase = (elapsed % cycleDuration) / cycleDuration; // 0.0 to 1.0

      // Physiological double-beat rhythm: S1 ("Lub"), brief recoil, S2 ("Dub"), diastolic rest
      var dilation = 0;
      var systolicPulse = 0;
      var apicalTorsion = 0;

      if (phase < 0.14) {
        // S1: Primary Ventricular Systole (Lub)
        var p1 = Math.sin((phase / 0.14) * Math.PI);
        dilation = p1 * 0.062;
        systolicPulse = p1;
        apicalTorsion = p1 * 0.028;
      } else if (phase >= 0.18 && phase < 0.32) {
        // S2: Semilunar Valve Closure (Dub)
        var p2 = Math.sin(((phase - 0.18) / 0.14) * Math.PI);
        dilation = p2 * 0.038;
        systolicPulse = p2 * 0.60;
        apicalTorsion = p2 * 0.016;
      } else {
        // Diastolic rest: Resting myocardial baseline with calm refilling
        dilation = 0;
        systolicPulse = 0;
        apicalTorsion = 0;
      }

      // Smooth mouse lerp
      mouseX += (targetMouseX - mouseX) * 0.05;
      mouseY += (targetMouseY - mouseY) * 0.05;

      // Anisotropic cardiac deformation (transverse expands more than longitudinal)
      var scaleX = baseScale * (1.0 + dilation);
      var scaleY = baseScale * (1.0 + dilation * 0.55);
      var scaleZ = baseScale * (1.0 + dilation * 1.10);
      heartGroup.scale.set(scaleX, scaleY, scaleZ);

      // Subtle biological elevation & idle yaw
      var floatY = Math.sin(elapsed * 1.2) * 0.030;
      var idleYaw = Math.sin(elapsed * 0.50) * 0.022;
      var idlePitch = Math.cos(elapsed * 0.65) * 0.012;

      heartGroup.position.y = floatY;
      heartGroup.rotation.y = baseRotY + idleYaw + apicalTorsion * 0.3 + mouseX * 0.14;
      heartGroup.rotation.x = baseRotX + idlePitch + mouseY * 0.07;
      heartGroup.rotation.z = baseRotZ + apicalTorsion;

      // Synchronize myocardial tissue emissive surge with cardiac pulse (restrained, no flash)
      if (heartMaterial) {
        heartMaterial.emissiveIntensity = 0.20 + systolicPulse * 0.25;
      }

      // Synchronize internal core light
      if (coreLight) {
        coreLight.intensity = 1.2 + systolicPulse * 2.0;
      }

      // Synchronize ambient background aura (subtle dark-red ambient glow)
      if (ambientGlow) {
        var glowOpacity = 0.20 + systolicPulse * 0.22;
        var glowScale = 1.0 + systolicPulse * 0.08;
        ambientGlow.style.opacity = glowOpacity.toFixed(3);
        ambientGlow.style.transform = 'scale(' + glowScale.toFixed(3) + ')';
      }

      // Synchronize ECG waveform pulse
      if (ecgTrace) {
        var ecgOpacity = 0.45 + systolicPulse * 0.35;
        ecgTrace.style.opacity = Math.min(1.0, ecgOpacity).toFixed(3);
      }

      renderer.render(scene, camera);
    }

    // 7. Responsive Resizing
    function handleResize() {
      if (!container || !renderer || !camera) return;
      var newWidth = container.clientWidth;
      var newHeight = container.clientHeight;
      if (newWidth === 0 || newHeight === 0) return;

      camera.aspect = newWidth / newHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(newWidth, newHeight);

      if (prefersReducedMotion) {
        renderer.render(scene, camera);
      }
    }

    window.addEventListener('resize', handleResize);

    if (window.ResizeObserver) {
      var ro = new ResizeObserver(handleResize);
      ro.observe(container);
    }
  }
})();
