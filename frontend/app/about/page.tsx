import { title } from "@/components/primitives";

export default function AboutPage() {
  return (
    <div className="space-y-6">
      <h1 className={title()}>About</h1>
      <p className="leading-relaxed text-muted-foreground">
        This implementation introduces a novel approach for synergistically
        integrating declarative process specifications and event processing
        within a unified engine paradigm. It leverages a single Complex Event
        Processing (CEP) engine (Esper) for both event abstraction and directly
        executing MP-Declare models. This method translates declarative
        constraints into executable CEP queries using a multi-level event
        abstraction framework.
      </p>
      <p className="leading-relaxed text-muted-foreground">
        The proof-of-concept demonstrates this integration, allowing MP-Declare
        models to be enacted directly from event streams. This approach aims to
        simplify architectures, reduce latency, and enable responsive,
        event-driven execution by removing the need for dedicated preprocessing
        middleware. The implementation validates the functional correctness of
        managing process constraints within a unified CEP environment, offering
        a foundational rethinking of event-based paradigms for process
        management in data-rich contexts.
      </p>

      <p>2025, the authors</p>
    </div>
  );
}
