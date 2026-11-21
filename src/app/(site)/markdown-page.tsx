import Markdown from "react-markdown";

/** Renders a Markdown document (privacy policy, terms) in the site's text style. */
export default function MarkdownPage({ text }: { text: string }) {
  return (
    <main className="mx-auto max-w-3xl px-4 py-12 sm:px-6">
      <Markdown
        components={{
          h1: (props) => <h1 className="text-3xl font-semibold tracking-tight" {...props} />,
          h2: (props) => <h2 className="mt-10 text-xl font-semibold" {...props} />,
          h3: (props) => <h3 className="mt-6 font-semibold" {...props} />,
          p: (props) => <p className="mt-4 leading-7 text-muted" {...props} />,
          ul: (props) => <ul className="mt-4 list-disc space-y-1 pl-6 text-muted" {...props} />,
          li: (props) => <li className="leading-7" {...props} />,
          a: (props) => <a className="text-accent underline" {...props} />,
          strong: (props) => <strong className="font-semibold text-foreground" {...props} />,
        }}
      >
        {text}
      </Markdown>
    </main>
  );
}
