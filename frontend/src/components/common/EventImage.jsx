import { useState } from 'react';

/** Event image with a graceful branded fallback when the URL fails to load. */
export function EventImage({ src, alt, title }) {
  const [failedSrc, setFailedSrc] = useState(null);

  if (!src || failedSrc === src) {
    return (
      <div className="image-fallback" aria-hidden="true">
        {(title || '?').charAt(0).toUpperCase()}
      </div>
    );
  }
  return <img src={src} alt={alt || ''} loading="lazy" decoding="async" onError={() => setFailedSrc(src)} />;
}
