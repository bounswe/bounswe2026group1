import 'package:flutter/material.dart';
import '../theme/app_colors.dart';

/// First-visit accessibility-standards tutorial for the mobile app.
///
/// Mirrors the web onboarding flow: 10 swipeable slides with a photo
/// diagram on top, a bullet list underneath, and a persistent citation
/// for Özlem Belir's Mimari Erişilebilirlik Kılavuzu. Every topic bullet
/// ends with `report as <name>` matching an IssueType in
/// `backend/src/main/java/com/bounswe2026group1/backend/model/IssueType.java`
/// so the tour doubles as a reporting reference.
///
/// Persistence is the caller's responsibility — HomeScreen sets the
/// shared_preferences flag once this route pops.
class OnboardingTutorialScreen extends StatefulWidget {
  const OnboardingTutorialScreen({super.key});

  @override
  State<OnboardingTutorialScreen> createState() =>
      _OnboardingTutorialScreenState();
}

class _OnboardingTutorialScreenState extends State<OnboardingTutorialScreen> {
  final PageController _controller = PageController();
  int _index = 0;

  late final List<_Slide> _slides = _buildSlides();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _goNext() {
    if (_index < _slides.length - 1) {
      _controller.animateToPage(
        _index + 1,
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOut,
      );
    } else {
      Navigator.of(context).maybePop();
    }
  }

  void _goBack() {
    if (_index > 0) {
      _controller.animateToPage(
        _index - 1,
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOut,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isLast = _index == _slides.length - 1;
    return Scaffold(
      backgroundColor: AppColors.surfaceContainerLowest,
      body: SafeArea(
        child: Column(
          children: [
            _TopBar(
              total: _slides.length,
              index: _index,
              onClose: () => Navigator.of(context).maybePop(),
            ),
            Expanded(
              child: PageView.builder(
                controller: _controller,
                onPageChanged: (i) => setState(() => _index = i),
                itemCount: _slides.length,
                itemBuilder: (context, i) => _SlideView(slide: _slides[i]),
              ),
            ),
            _Attribution(),
            _BottomBar(
              isLast: isLast,
              isFirst: _index == 0,
              onSkip: () => Navigator.of(context).maybePop(),
              onBack: _goBack,
              onPrimary: _goNext,
            ),
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slide data
// ─────────────────────────────────────────────────────────────────────────────

/// Reportable issue audience tag — drives the small coloured chip at the
/// end of each bullet so users with that need can spot relevant rules.
enum _Audience { mob, vis, hear, eld }

class _Bullet {
  /// Markdown-style text. `**bold**` segments render in the primary-dim
  /// colour to highlight the issue type and key dimensions.
  final String text;
  final List<_Audience> audiences;
  const _Bullet(this.text, this.audiences);
}

class _Slide {
  final String id;
  final String title;
  final IconData icon;
  final Color badgeColor;

  /// Asset path for the diagram. Either this or [heroBuilder] must be set.
  final String? image;
  final String? imageSemanticLabel;

  /// Custom widget used in place of an image (welcome + how-to slides).
  final Widget Function(BuildContext)? heroBuilder;

  final List<_Bullet> bullets;
  final String? caption;
  final String? tipTitle;
  final String? tip;

  const _Slide({
    required this.id,
    required this.title,
    required this.icon,
    required this.badgeColor,
    this.image,
    this.imageSemanticLabel,
    this.heroBuilder,
    this.bullets = const [],
    this.caption,
    this.tipTitle,
    this.tip,
  });
}

List<_Slide> _buildSlides() {
  return <_Slide>[
    _Slide(
      id: 'welcome',
      title: 'Welcome to Mapcess',
      icon: Icons.explore,
      badgeColor: AppColors.primary,
      heroBuilder: (_) => const _WelcomeHero(),
      tipTitle: "What you'll learn",
      tip: "A quick tour of the most common things people report — ramps, "
          "curb cuts, sidewalks, elevators, and doors — and how to file your "
          "first report in under a minute. The diagrams are adapted from "
          "Özlem Belir's Mimari Erişilebilirlik Kılavuzu.",
    ),
    _Slide(
      id: 'ramps',
      title: 'Ramps',
      icon: Icons.accessible_forward,
      badgeColor: const Color(0xFF2E7D32),
      image: 'assets/images/tutorial/ramp-dimensions.png',
      imageSemanticLabel:
          'Isometric ramp diagram showing handrail heights, max slope 1:12, '
          '9 m max run with 1.5 m landings',
      caption: 'Max slope **1:12** · max run **9 m** · landings **≥ 1.5 m**',
      bullets: const [
        _Bullet('Slope steeper than **1:12** — report as **too steep**',
            [_Audience.mob]),
        _Bullet(
            'Run longer than **9 m** without a **≥ 1.5 m** landing — '
            'report as **no landing**',
            [_Audience.mob]),
        _Bullet('No handrails — or only one side — report as **missing handrail**',
            [_Audience.mob, _Audience.eld, _Audience.vis]),
        _Bullet(
            'Handrails present but mounted below **90 cm** — '
            'report as **handrail too low**',
            [_Audience.mob, _Audience.eld]),
        _Bullet(
            'Clear width between handrails under **87 cm** — '
            'report as **too narrow**',
            [_Audience.mob]),
      ],
    ),
    _Slide(
      id: 'ramps-detail',
      title: 'Ramps — landings & stairs',
      icon: Icons.stairs,
      badgeColor: const Color(0xFF2E7D32),
      image: 'assets/images/tutorial/ramp-detail.png',
      imageSemanticLabel:
          'Detailed cutaway of a ramp meeting a stair: handrails at 900 mm, '
          'landings at 1200 mm, anti-slip treads, tactile paving, and a side '
          'profile or curb',
      caption: 'Where the ramp meets stairs and a doorway',
      bullets: const [
        _Bullet(
            'Landing smaller than **120 cm** at the top or bottom — '
            'report as **no landing**',
            [_Audience.mob]),
        _Bullet('Ramp width under **150 cm** — report as **too narrow**',
            [_Audience.mob]),
        _Bullet(
            'Stair steps without anti-slip strips — report as **no anti-slip**',
            [_Audience.mob, _Audience.eld, _Audience.vis]),
        _Bullet(
            'Stair edges without a contrasting **nosing strip** — '
            'report as **missing nosing strip**',
            [_Audience.vis, _Audience.eld]),
        _Bullet(
            'Step risers above **16 cm** or treads under **27 cm** — '
            'report as **riser too high** / **tread too shallow**',
            [_Audience.mob, _Audience.eld]),
        _Bullet(
            'Steps of inconsistent size or with open vertical faces — '
            'report as **irregular steps** / **open risers**',
            [_Audience.mob, _Audience.eld, _Audience.vis]),
      ],
    ),
    _Slide(
      id: 'curb-cuts',
      title: 'Curb cuts',
      icon: Icons.roundabout_right,
      badgeColor: const Color(0xFF176A21),
      image: 'assets/images/tutorial/curb-ramp.png',
      imageSemanticLabel:
          'Wheelchair user crossing a curb ramp with a 1:10 slope and minimum '
          '915 mm bottom width',
      caption:
          'Where the sidewalk meets the crossing · **1:10** slope · '
          '**≥ 915 mm** wide',
      bullets: const [
        _Bullet('Slope steeper than **1:10** — report as **too steep**',
            [_Audience.mob]),
        _Bullet('Bottom width under **91 cm** — report as **too narrow**',
            [_Audience.mob]),
        _Bullet(
            'No **tactile warning surface** where the curb meets the road — '
            'report as **no tactile warning**',
            [_Audience.vis]),
        _Bullet(
            'Raised lip at the gutter that catches wheels — '
            'report as **raised lip**',
            [_Audience.mob]),
        _Bullet(
            'No curb cut at all where one is needed — report as **missing**',
            [_Audience.mob, _Audience.vis]),
      ],
    ),
    _Slide(
      id: 'sidewalks',
      title: 'Sidewalks',
      icon: Icons.directions_walk,
      badgeColor: const Color(0xFF495F69),
      image: 'assets/images/tutorial/street-view.png',
      imageSemanticLabel:
          'Three-dimensional street view showing sign and awning heights and '
          'clear pedestrian path widths',
      caption: 'Street view — signs and awnings stay above head height',
      bullets: const [
        _Bullet(
            'Clear path narrower than **1.2 m** — report as **too narrow**',
            [_Audience.mob]),
        _Bullet(
            'Awnings, signs or branches under **2.2 m** overhead — '
            'report as **insufficient clearance**',
            [_Audience.mob, _Audience.vis, _Audience.eld]),
        _Bullet(
            'Parked vehicles, furniture, or debris in the path — '
            'report as **blocked**',
            [_Audience.mob, _Audience.vis]),
        _Bullet(
            'Cracks, potholes, or damage that could trip someone — '
            'report as **uneven surface**',
            [_Audience.mob, _Audience.eld, _Audience.vis]),
        _Bullet('Slippery when wet or worn smooth — report as **slippery surface**',
            [_Audience.mob, _Audience.eld]),
        _Bullet(
            'No tactile paving along the route — report as **no tactile paving**',
            [_Audience.vis]),
        _Bullet(
            'Level change over **1.3 cm** that isn\'t ramped — '
            'report as **unramped level difference**',
            [_Audience.mob]),
      ],
    ),
    _Slide(
      id: 'elevators-cabin',
      title: 'Elevators — cabin',
      icon: Icons.elevator,
      badgeColor: const Color(0xFFB02500),
      image: 'assets/images/tutorial/elevator-cabin.png',
      imageSemanticLabel:
          'Top-down elevator cabin: 1730 mm wide by 1370 mm deep with a 915 mm '
          'clear door opening',
      caption: 'Cabin **≥ 173 × 137 cm** · door **≥ 915 mm**',
      bullets: const [
        _Bullet(
            'Cabin narrower than **120 cm** or under **1.80 m²** floor area — '
            'report as **cabin too small**',
            [_Audience.mob]),
        _Bullet('Door opening under **90 cm** — report as **door too narrow**',
            [_Audience.mob]),
        _Bullet(
            'Approach area in front of the doors under **120–150 cm** — '
            'report as **insufficient landing**',
            [_Audience.mob]),
        _Bullet('No grab bar inside the cabin — report as **no grab bar**',
            [_Audience.mob, _Audience.eld]),
        _Bullet(
            'Elevator exists but isn\'t working — report as **out of service**',
            [_Audience.mob, _Audience.vis, _Audience.eld]),
      ],
    ),
    _Slide(
      id: 'elevators-panel',
      title: 'Elevators — entrance & panel',
      icon: Icons.elevator,
      badgeColor: const Color(0xFFB02500),
      image: 'assets/images/tutorial/elevator-entrance.png',
      imageSemanticLabel:
          'Elevator entrance: 1830 mm door height, 1500 mm panel height, '
          '735 mm button height, up/down arrow indicator',
      caption:
          'Buttons at **~735 mm** · panel top **≤ 1500 mm** · door '
          '**≥ 1830 mm** high',
      bullets: const [
        _Bullet(
            'Buttons mounted outside **85–120 cm** — '
            'report as **button height invalid**',
            [_Audience.mob]),
        _Bullet(
            'No Braille or raised tactile labels on the buttons — '
            'report as **no braille**',
            [_Audience.vis]),
        _Bullet('No spoken floor announcement — report as **no audio**',
            [_Audience.vis]),
        _Bullet(
            'Cabin or controls poorly lit — report as **insufficient lighting**',
            [_Audience.vis, _Audience.eld]),
      ],
    ),
    _Slide(
      id: 'doors-glass',
      title: 'Doors — glass & contrast',
      icon: Icons.door_front_door,
      badgeColor: const Color(0xFF8B6A00),
      image: 'assets/images/tutorial/door-glass.png',
      imageSemanticLabel:
          'Front view of a glass entry door showing horizontal contrast bands '
          'between 500 mm and 1.5 m and a lever handle',
      caption: 'Contrast bands between **50 cm** and **1.5 m**',
      bullets: const [
        _Bullet(
            'Clear width under **90 cm** when fully open — report as **too narrow**',
            [_Audience.mob]),
        _Bullet(
            'Large glass surfaces without contrast bands — '
            'report as **no glass marking**',
            [_Audience.vis]),
        _Bullet(
            'Threshold sill higher than **0.6 cm** — report as **high threshold**',
            [_Audience.mob]),
        _Bullet(
            'Steps at the entrance where a ramp is needed — '
            'report as **step at entrance**',
            [_Audience.mob]),
        _Bullet(
            'Building entrance without an automatic / sensor door — '
            'report as **no automatic door**',
            [_Audience.mob, _Audience.eld]),
      ],
    ),
    _Slide(
      id: 'doors-handles',
      title: 'Doors — handles',
      icon: Icons.door_back_door,
      badgeColor: const Color(0xFF8B6A00),
      image: 'assets/images/tutorial/door-handles.png',
      imageSemanticLabel:
          'Comparison of door hardware: a round knob crossed out as inaccessible, '
          'contrasted with two long lever handles',
      caption:
          'Knob (left): **avoid** · Lever / pull bar (right): **accessible**',
      bullets: const [
        _Bullet(
            'Round knob instead of a lever or pull-bar — '
            'report as **no lever handle**',
            [_Audience.mob, _Audience.eld]),
        _Bullet(
            'Handle mounted outside **90–110 cm** — '
            'report as **handle height invalid**',
            [_Audience.mob]),
        _Bullet(
            'Door needs excessive force to open — report as **heavy door**',
            [_Audience.mob, _Audience.eld]),
        _Bullet(
            'Intercom or doorbell outside **90–140 cm** or not approachable '
            'from the side — report as **intercom inaccessible**',
            [_Audience.mob]),
      ],
    ),
    _Slide(
      id: 'how-to',
      title: 'How to file a report',
      icon: Icons.add_location_alt,
      badgeColor: AppColors.primary,
      heroBuilder: (_) => const _HowToHero(),
      tipTitle: 'Done in under a minute',
      tip: 'Once five neighbours agree, your report becomes verified and '
          'starts shaping accessible routes for everyone.',
    ),
  ];
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout helpers
// ─────────────────────────────────────────────────────────────────────────────

class _TopBar extends StatelessWidget {
  final int total;
  final int index;
  final VoidCallback onClose;

  const _TopBar({
    required this.total,
    required this.index,
    required this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 8, 4),
      child: Row(
        children: [
          Expanded(child: _DotIndicator(total: total, index: index)),
          IconButton(
            tooltip: 'Close tutorial',
            onPressed: onClose,
            icon: const Icon(Icons.close),
            color: AppColors.onSurfaceVariant,
          ),
        ],
      ),
    );
  }
}

class _DotIndicator extends StatelessWidget {
  final int total;
  final int index;

  const _DotIndicator({required this.total, required this.index});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: List.generate(total, (i) {
        final active = i == index;
        return Padding(
          padding: const EdgeInsets.only(right: 6),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 180),
            height: 8,
            width: active ? 24 : 8,
            decoration: BoxDecoration(
              color: active
                  ? AppColors.primary
                  : AppColors.surfaceContainerHigh,
              borderRadius: BorderRadius.circular(999),
            ),
          ),
        );
      }),
    );
  }
}

class _SlideView extends StatelessWidget {
  final _Slide slide;
  const _SlideView({required this.slide});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _TitleStrip(slide: slide),
          const SizedBox(height: 12),
          _DiagramCard(slide: slide),
          if (slide.caption != null) ...[
            const SizedBox(height: 8),
            _Caption(text: slide.caption!),
          ],
          if (slide.bullets.isNotEmpty) ...[
            const SizedBox(height: 14),
            ...slide.bullets.map((b) => _BulletRow(bullet: b)),
          ],
          if (slide.tip != null) ...[
            const SizedBox(height: 14),
            _TipCard(title: slide.tipTitle ?? 'Tip', body: slide.tip!),
          ],
        ],
      ),
    );
  }
}

class _TitleStrip extends StatelessWidget {
  final _Slide slide;
  const _TitleStrip({required this.slide});

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: slide.badgeColor.withValues(alpha: 0.14),
            shape: BoxShape.circle,
          ),
          alignment: Alignment.center,
          child: Icon(slide.icon, size: 26, color: slide.badgeColor),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            slide.title,
            style: TextStyle(
              color: AppColors.onSurface,
              fontSize: 20,
              fontWeight: FontWeight.w700,
              fontFamily: 'Plus Jakarta Sans',
            ),
          ),
        ),
      ],
    );
  }
}

class _DiagramCard extends StatelessWidget {
  final _Slide slide;
  const _DiagramCard({required this.slide});

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    // Cap the diagram height to roughly a third of the viewport so the
    // bullets remain visible without forcing the user to scroll past
    // an oversized image on tall phones.
    final maxHeight = (media.size.height * 0.34).clamp(220.0, 360.0);

    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.surfaceContainerHigh),
      ),
      padding: const EdgeInsets.all(10),
      child: Container(
        constraints: BoxConstraints(maxHeight: maxHeight),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(8),
        ),
        padding: const EdgeInsets.all(10),
        alignment: Alignment.center,
        child: slide.image != null
            ? Image.asset(
                slide.image!,
                fit: BoxFit.contain,
                semanticLabel: slide.imageSemanticLabel,
              )
            : (slide.heroBuilder?.call(context) ?? const SizedBox.shrink()),
      ),
    );
  }
}

class _Caption extends StatelessWidget {
  final String text;
  const _Caption({required this.text});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: RichText(
        textAlign: TextAlign.center,
        text: TextSpan(
          children: _spansFromMarkdown(
            text,
            base: TextStyle(
              color: AppColors.onSurfaceVariant,
              fontSize: 13,
              fontWeight: FontWeight.w500,
              fontFamily: 'Inter',
            ),
            bold: TextStyle(
              color: AppColors.primaryDim,
              fontSize: 13,
              fontWeight: FontWeight.w700,
              fontFamily: 'Inter',
            ),
          ),
        ),
      ),
    );
  }
}

class _BulletRow extends StatelessWidget {
  final _Bullet bullet;
  const _BulletRow({required this.bullet});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(top: 2),
            child: Icon(Icons.check_circle, size: 18, color: AppColors.primary),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: RichText(
              text: TextSpan(
                children: _spansFromMarkdown(
                  bullet.text,
                  base: TextStyle(
                    color: AppColors.onSurface,
                    fontSize: 13.5,
                    fontFamily: 'Inter',
                    height: 1.35,
                  ),
                  bold: TextStyle(
                    color: AppColors.primaryDim,
                    fontSize: 13.5,
                    fontWeight: FontWeight.w700,
                    fontFamily: 'Inter',
                    height: 1.35,
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(width: 6),
          Wrap(
            spacing: 4,
            children:
                bullet.audiences.map((a) => _AudienceChip(audience: a)).toList(),
          ),
        ],
      ),
    );
  }
}

class _AudienceChip extends StatelessWidget {
  final _Audience audience;
  const _AudienceChip({required this.audience});

  static const _meta = <_Audience, (IconData, Color, Color)>{
    _Audience.mob: (Icons.accessible, Color(0xFFDAEFDB), Color(0xFF1B5E20)),
    _Audience.vis: (Icons.visibility, Color(0xFFD4E6F8), Color(0xFF0D47A1)),
    _Audience.hear: (Icons.hearing, Color(0xFFFBE0DA), Color(0xFF8D2200)),
    _Audience.eld: (Icons.elderly, Color(0xFFE8DEF8), Color(0xFF3E1F8A)),
  };

  @override
  Widget build(BuildContext context) {
    final (icon, bg, fg) = _meta[audience]!;
    return Container(
      width: 24,
      height: 24,
      decoration: BoxDecoration(color: bg, shape: BoxShape.circle),
      alignment: Alignment.center,
      child: Icon(icon, size: 14, color: fg),
    );
  }
}

class _TipCard extends StatelessWidget {
  final String title;
  final String body;
  const _TipCard({required this.title, required this.body});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(8),
        border: Border(left: BorderSide(color: AppColors.primary, width: 3)),
      ),
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title.toUpperCase(),
            style: TextStyle(
              color: AppColors.primaryDim,
              fontSize: 10.5,
              fontWeight: FontWeight.w800,
              letterSpacing: 1.2,
              fontFamily: 'Inter',
            ),
          ),
          const SizedBox(height: 4),
          Text(
            body,
            style: TextStyle(
              color: AppColors.onSurface,
              fontSize: 13,
              height: 1.5,
              fontFamily: 'Inter',
            ),
          ),
        ],
      ),
    );
  }
}

class _Attribution extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
      child: RichText(
        textAlign: TextAlign.center,
        text: TextSpan(
          style: TextStyle(
            color: AppColors.onSurfaceVariant,
            fontSize: 11,
            fontStyle: FontStyle.italic,
            fontFamily: 'Inter',
          ),
          children: [
            const TextSpan(text: 'Diagrams adapted from '),
            TextSpan(
              text: 'Özlem Belir',
              style: TextStyle(
                fontStyle: FontStyle.normal,
                fontWeight: FontWeight.w600,
                color: AppColors.onSurface,
              ),
            ),
            const TextSpan(text: ', Mimari Erişilebilirlik Kılavuzu'),
          ],
        ),
      ),
    );
  }
}

class _BottomBar extends StatelessWidget {
  final bool isLast;
  final bool isFirst;
  final VoidCallback onSkip;
  final VoidCallback onBack;
  final VoidCallback onPrimary;

  const _BottomBar({
    required this.isLast,
    required this.isFirst,
    required this.onSkip,
    required this.onBack,
    required this.onPrimary,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
      decoration: BoxDecoration(
        border: Border(top: BorderSide(color: AppColors.surfaceContainer)),
      ),
      child: Row(
        children: [
          Opacity(
            opacity: isLast ? 0 : 1,
            child: IgnorePointer(
              ignoring: isLast,
              child: TextButton(
                onPressed: onSkip,
                style: TextButton.styleFrom(
                  foregroundColor: AppColors.onSurfaceVariant,
                ),
                child: const Text('Skip tour'),
              ),
            ),
          ),
          const Spacer(),
          Opacity(
            opacity: isFirst ? 0 : 1,
            child: IgnorePointer(
              ignoring: isFirst,
              child: TextButton(
                onPressed: onBack,
                style: TextButton.styleFrom(
                  foregroundColor: AppColors.onSurface,
                  backgroundColor: AppColors.surfaceContainer,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
                  shape: const RoundedRectangleBorder(
                    borderRadius: BorderRadius.all(Radius.circular(999)),
                  ),
                ),
                child: const Text('Back'),
              ),
            ),
          ),
          const SizedBox(width: 8),
          FilledButton.icon(
            onPressed: onPrimary,
            style: FilledButton.styleFrom(
              backgroundColor: AppColors.primary,
              foregroundColor: AppColors.onPrimarySolid,
              padding:
                  const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
              shape: const RoundedRectangleBorder(
                borderRadius: BorderRadius.all(Radius.circular(999)),
              ),
            ),
            icon: Icon(isLast ? Icons.check : Icons.arrow_forward, size: 18),
            label: Text(isLast ? 'Got it' : 'Next'),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Welcome + How-to heroes (slides without a photo diagram)
// ─────────────────────────────────────────────────────────────────────────────

class _WelcomeHero extends StatelessWidget {
  const _WelcomeHero();

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Container(
          width: 110,
          height: 110,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: LinearGradient(
              colors: [AppColors.primary, AppColors.primaryAccent],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
          ),
          alignment: Alignment.center,
          child: Icon(Icons.explore,
              size: 56, color: AppColors.onPrimarySolid),
        ),
        const SizedBox(height: 14),
        Text(
          'Accessibility, mapped by neighbours.',
          textAlign: TextAlign.center,
          style: TextStyle(
            color: AppColors.onSurfaceVariant,
            fontSize: 14,
            fontWeight: FontWeight.w500,
            fontFamily: 'Inter',
          ),
        ),
      ],
    );
  }
}

class _HowToHero extends StatelessWidget {
  const _HowToHero();

  @override
  Widget build(BuildContext context) {
    const steps = <(IconData, String)>[
      (Icons.add_location_alt, 'Add report'),
      (Icons.place, 'Pick spot'),
      (Icons.add_a_photo, 'Add media'),
      (Icons.edit_note, 'Describe'),
      (Icons.send, 'Submit'),
    ];

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Wrap(
        alignment: WrapAlignment.center,
        crossAxisAlignment: WrapCrossAlignment.center,
        spacing: 6,
        runSpacing: 12,
        children: [
          for (var i = 0; i < steps.length; i++) ...[
            _StepTile(icon: steps[i].$1, label: steps[i].$2),
            if (i < steps.length - 1)
              Icon(Icons.chevron_right,
                  size: 22, color: AppColors.onSurfaceVariant),
          ],
        ],
      ),
    );
  }
}

class _StepTile extends StatelessWidget {
  final IconData icon;
  final String label;
  const _StepTile({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minWidth: 88),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 28, color: AppColors.primary),
          const SizedBox(height: 6),
          Text(
            label,
            style: TextStyle(
              color: AppColors.onSurface,
              fontSize: 12,
              fontWeight: FontWeight.w600,
              fontFamily: 'Inter',
            ),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rich-text helper: ** turns the next segment bold until the closing **.
// ─────────────────────────────────────────────────────────────────────────────

List<TextSpan> _spansFromMarkdown(
  String input, {
  required TextStyle base,
  required TextStyle bold,
}) {
  final spans = <TextSpan>[];
  final parts = input.split('**');
  for (var i = 0; i < parts.length; i++) {
    if (parts[i].isEmpty) continue;
    spans.add(TextSpan(text: parts[i], style: i.isOdd ? bold : base));
  }
  return spans;
}
